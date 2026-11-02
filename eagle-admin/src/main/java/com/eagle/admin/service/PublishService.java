package com.eagle.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eagle.admin.entity.ConfigHistory;
import com.eagle.admin.entity.ConfigInfo;
import com.eagle.admin.entity.ConfigPublish;
import com.eagle.admin.mapper.ConfigHistoryMapper;
import com.eagle.admin.mapper.ConfigInfoMapper;
import com.eagle.admin.mapper.ConfigPublishMapper;
import com.eagle.admin.vo.request.ConfigSaveRequest;
import com.eagle.admin.vo.request.ConfigUpdateRequest;
import com.eagle.admin.vo.request.PublishQueryRequest;
import com.eagle.admin.vo.response.ConfigVO;
import com.eagle.admin.vo.response.PageResult;
import com.eagle.admin.vo.response.PublishVO;
import com.eagle.common.dto.ConfigNodeDTO;
import com.eagle.common.enums.OperationType;
import com.eagle.common.enums.PublishStatus;
import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;
import com.eagle.common.util.ValueTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 配置发布服务（核心）。
 * <p>发布流程（文档 4.4）:</p>
 * <ol>
 *     <li>类型强校验（ValueTypeValidator，string/number/boolean）</li>
 *     <li>数据库事务: upsert t_config_info（软删行复用避免唯一键冲突）→ 版本快照写t_config_history → 发布记录写t_config_publish(待发布)</li>
 *     <li>事务提交后同步ZK（幂等写），失败则发布记录标记失败 + 触发告警，支持手动重试</li>
 * </ol>
 */
@Slf4j
@Service
public class PublishService {

    private static final int MAX_MSG_LENGTH = 500;

    private final ConfigInfoMapper configInfoMapper;
    private final ConfigHistoryMapper configHistoryMapper;
    private final ConfigPublishMapper configPublishMapper;
    private final ZkSyncService zkSyncService;
    private final AlertNotifier alertNotifier;
    private final ConfigVoConverter converter;
    private final TransactionTemplate transactionTemplate;

    public PublishService(ConfigInfoMapper configInfoMapper,
                          ConfigHistoryMapper configHistoryMapper,
                          ConfigPublishMapper configPublishMapper,
                          ZkSyncService zkSyncService,
                          AlertNotifier alertNotifier,
                          ConfigVoConverter converter,
                          PlatformTransactionManager transactionManager) {
        this.configInfoMapper = configInfoMapper;
        this.configHistoryMapper = configHistoryMapper;
        this.configPublishMapper = configPublishMapper;
        this.zkSyncService = zkSyncService;
        this.alertNotifier = alertNotifier;
        this.converter = converter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ==================== 新增 ====================

    /**
     * 新增配置并发布
     */
    public ConfigVO saveAndPublish(ConfigSaveRequest req) {
        ValueTypeValidator.checkAndGet(req.getValueType(), req.getConfigValue());
        if (selectActive(req.getEnv(), req.getGroupId(), req.getConfigKey()) != null) {
            throw new EagleException(ErrorCode.CONFIG_EXISTS,
                    String.format("env=%s, groupId=%s, configKey=%s", req.getEnv(), req.getGroupId(), req.getConfigKey()));
        }
        String operator = normalizeOperator(req.getOperator());
        ConfigInfo info = new ConfigInfo();
        info.setEnv(req.getEnv());
        info.setGroupId(req.getGroupId());
        info.setConfigKey(req.getConfigKey());
        info.setConfigValue(req.getConfigValue());
        info.setValueType(req.getValueType());
        info.setDescription(req.getDescription() == null ? "" : req.getDescription());
        info.setCreatedBy(operator);
        info.setUpdatedBy(operator);
        PublishContext ctx = transactionTemplate.execute(status -> doCreateSnapshot(info, OperationType.UPDATE, operator));
        syncZkAndUpdateStatus(ctx);
        return buildConfigVO(ctx);
    }

    // ==================== 编辑 ====================

    /**
     * 编辑配置并发布（允许修改类型，但值必须与新类型匹配）
     */
    public ConfigVO updateAndPublish(Long id, ConfigUpdateRequest req) {
        ValueTypeValidator.checkAndGet(req.getValueType(), req.getConfigValue());
        ConfigInfo info = requireConfig(id);
        String operator = normalizeOperator(req.getOperator());
        info.setConfigValue(req.getConfigValue());
        info.setValueType(req.getValueType());
        if (req.getDescription() != null) {
            info.setDescription(req.getDescription());
        }
        info.setUpdatedBy(operator);
        PublishContext ctx = transactionTemplate.execute(status -> doCreateSnapshot(info, OperationType.UPDATE, operator));
        syncZkAndUpdateStatus(ctx);
        return buildConfigVO(ctx);
    }

    // ==================== 删除 ====================

    /**
     * 删除配置并发布: 软删主表 + DELETE版本快照 + 删除ZK节点
     */
    public ConfigVO deleteAndPublish(Long id, String operator) {
        final String op = normalizeOperator(operator);
        ConfigInfo info = requireConfig(id);
        PublishContext ctx = transactionTemplate.execute(status -> {
            configInfoMapper.deleteById(info.getId());
            return doCreateSnapshot(info, OperationType.DELETE, op);
        });
        syncZkAndUpdateStatus(ctx);
        // info在软删前已捕获，直接组装视图（latestVersion来自持久化的历史表）
        return converter.toVO(info, ctx.getPublishId());
    }

    // ==================== 回滚 ====================

    /**
     * 回滚到指定历史快照: 以快照值重新发布（生成新版本号），同步更新ZK
     */
    public ConfigVO rollback(Long configId, Long historyId, String operator) {
        final String op = normalizeOperator(operator);
        ConfigInfo info = requireConfig(configId);
        ConfigHistory snapshot = configHistoryMapper.selectById(historyId);
        if (snapshot == null || !Objects.equals(snapshot.getConfigId(), configId)) {
            throw new EagleException(ErrorCode.NOT_FOUND, "历史版本不存在或不属于该配置: historyId=" + historyId);
        }
        info.setConfigValue(snapshot.getConfigValue());
        info.setValueType(snapshot.getValueType());
        info.setUpdatedBy(op);
        PublishContext ctx = transactionTemplate.execute(status -> doCreateSnapshot(info, OperationType.UPDATE, op));
        syncZkAndUpdateStatus(ctx);
        return buildConfigVO(ctx);
    }

    // ==================== 重试 ====================

    /**
     * 发布失败手动重试: 重新同步ZK并更新发布状态
     */
    public PublishVO retry(Long publishId) {
        ConfigPublish publish = configPublishMapper.selectById(publishId);
        if (publish == null) {
            throw new EagleException(ErrorCode.NOT_FOUND, "发布记录不存在: " + publishId);
        }
        if (publish.getPublishStatus() == null || publish.getPublishStatus() != PublishStatus.FAILED.getCode()) {
            throw new EagleException(ErrorCode.STATE_ERROR,
                    "仅失败的发布记录支持重试，当前状态: " + publish.getPublishStatus());
        }
        ConfigHistory history = configHistoryMapper.selectById(publish.getConfigHistoryId());
        if (history == null) {
            throw new EagleException(ErrorCode.NOT_FOUND, "发布记录关联的版本快照不存在: " + publish.getConfigHistoryId());
        }
        try {
            if (OperationType.DELETE.getCode().equals(history.getOperation())) {
                zkSyncService.deleteNode(publish.getEnv(), publish.getGroupId(), publish.getConfigKey());
            } else {
                zkSyncService.writeNode(publish.getEnv(), publish.getGroupId(), publish.getConfigKey(),
                        new ConfigNodeDTO(history.getValueType(), history.getConfigValue()));
            }
            configPublishMapper.updateStatus(publishId, PublishStatus.SUCCESS.getCode(), null);
        } catch (Exception e) {
            log.error("发布重试失败, publishId={}", publishId, e);
            String msg = truncate(e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            configPublishMapper.updateStatus(publishId, PublishStatus.FAILED.getCode(), msg);
            alertNotifier.alert(String.format("发布重试仍失败: publishId=%d, key=%s/%s/%s, 原因: %s",
                    publishId, publish.getEnv(), publish.getGroupId(), publish.getConfigKey(), msg));
            throw new EagleException(ErrorCode.ZK_SYNC_FAILED, msg);
        }
        return converter.toPublishVO(configPublishMapper.selectById(publishId), history);
    }

    // ==================== 发布记录查询 ====================

    public PageResult<PublishVO> page(PublishQueryRequest req) {
        Page<ConfigPublish> page = new Page<>(req.getPageNum(), req.getPageSize());
        LambdaQueryWrapper<ConfigPublish> wrapper = new LambdaQueryWrapper<ConfigPublish>()
                .eq(StringUtils.hasText(req.getEnv()), ConfigPublish::getEnv, req.getEnv())
                .eq(StringUtils.hasText(req.getGroupId()), ConfigPublish::getGroupId, req.getGroupId())
                .like(StringUtils.hasText(req.getConfigKey()), ConfigPublish::getConfigKey, req.getConfigKey())
                .eq(req.getPublishStatus() != null, ConfigPublish::getPublishStatus, req.getPublishStatus())
                .orderByDesc(ConfigPublish::getId);
        Page<ConfigPublish> result = configPublishMapper.selectPage(page, wrapper);
        List<Long> historyIds = result.getRecords().stream()
                .map(ConfigPublish::getConfigHistoryId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ConfigHistory> historyMap = historyIds.isEmpty() ? Map.of()
                : configHistoryMapper.selectBatchIds(historyIds).stream()
                        .collect(Collectors.toMap(ConfigHistory::getId, Function.identity()));
        List<PublishVO> vos = result.getRecords().stream()
                .map(p -> converter.toPublishVO(p, historyMap.get(p.getConfigHistoryId())))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), vos);
    }

    // ==================== 内部方法 ====================

    /**
     * 数据库事务内的快照落库（由TransactionTemplate回调执行）:
     * upsert主表 → 生成新版本号快照 → 记录发布(待发布)
     */
    private PublishContext doCreateSnapshot(ConfigInfo info, OperationType operation, String operator) {
        if (OperationType.UPDATE.equals(operation)) {
            if (info.getId() == null) {
                // 新增: 若唯一键被软删行占用则复用并恢复该行，避免唯一键冲突
                ConfigInfo deletedRow = configInfoMapper.selectIncludeDeleted(
                        info.getEnv(), info.getGroupId(), info.getConfigKey());
                if (deletedRow != null) {
                    info.setId(deletedRow.getId());
                    configInfoMapper.reviveDeleted(info);
                } else {
                    configInfoMapper.insert(info);
                }
            } else {
                configInfoMapper.updateById(info);
            }
        }
        int nextVersion = configHistoryMapper.selectMaxVersion(info.getId()) + 1;
        ConfigHistory history = new ConfigHistory();
        history.setConfigId(info.getId());
        history.setEnv(info.getEnv());
        history.setGroupId(info.getGroupId());
        history.setConfigKey(info.getConfigKey());
        history.setConfigValue(info.getConfigValue());
        history.setValueType(info.getValueType());
        history.setItemVersion(nextVersion);
        history.setOperation(operation.getCode());
        history.setOperator(operator);
        configHistoryMapper.insert(history);

        ConfigPublish publish = new ConfigPublish();
        publish.setEnv(info.getEnv());
        publish.setGroupId(info.getGroupId());
        publish.setConfigKey(info.getConfigKey());
        publish.setConfigHistoryId(history.getId());
        publish.setPublishStatus(PublishStatus.PENDING.getCode());
        configPublishMapper.insert(publish);

        ConfigNodeDTO node = OperationType.DELETE.equals(operation) ? null
                : new ConfigNodeDTO(info.getValueType(), info.getConfigValue());
        return new PublishContext(info.getId(), publish.getId(), info.getEnv(), info.getGroupId(),
                info.getConfigKey(), operation, node);
    }

    /**
     * 事务提交后同步ZK并回写发布状态; ZK失败不回滚DB，仅标记失败+告警（支持手动重试）
     */
    private void syncZkAndUpdateStatus(PublishContext ctx) {
        try {
            if (OperationType.DELETE.equals(ctx.getOperation())) {
                zkSyncService.deleteNode(ctx.getEnv(), ctx.getGroupId(), ctx.getConfigKey());
            } else {
                zkSyncService.writeNode(ctx.getEnv(), ctx.getGroupId(), ctx.getConfigKey(), ctx.getNode());
            }
            configPublishMapper.updateStatus(ctx.getPublishId(), PublishStatus.SUCCESS.getCode(), null);
        } catch (Exception e) {
            log.error("配置同步ZK失败, publishId={}, path={}/{}/{}", ctx.getPublishId(),
                    ctx.getEnv(), ctx.getGroupId(), ctx.getConfigKey(), e);
            String msg = truncate(e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            configPublishMapper.updateStatus(ctx.getPublishId(), PublishStatus.FAILED.getCode(), msg);
            alertNotifier.alert(String.format("配置同步ZK失败: publishId=%d, path=%s, 原因: %s（配置已入库，请到后台手动重试）",
                    ctx.getPublishId(), zkSyncService.buildPath(ctx.getEnv(), ctx.getGroupId(), ctx.getConfigKey()), msg));
            // 不抛异常: 配置已入库、发布记录已标记失败，接口正常返回失败状态供前端展示重试入口
        }
    }

    private ConfigVO buildConfigVO(PublishContext ctx) {
        ConfigInfo info = configInfoMapper.selectById(ctx.getConfigId());
        return converter.toVO(info, ctx.getPublishId());
    }

    private ConfigInfo requireConfig(Long id) {
        ConfigInfo info = configInfoMapper.selectById(id);
        if (info == null) {
            throw new EagleException(ErrorCode.NOT_FOUND, "配置不存在或已删除: id=" + id);
        }
        return info;
    }

    private ConfigInfo selectActive(String env, String groupId, String configKey) {
        return configInfoMapper.selectOne(new LambdaQueryWrapper<ConfigInfo>()
                .eq(ConfigInfo::getEnv, env)
                .eq(ConfigInfo::getGroupId, groupId)
                .eq(ConfigInfo::getConfigKey, configKey));
    }

    private String normalizeOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "admin";
    }

    private String truncate(String msg) {
        return msg.length() > MAX_MSG_LENGTH ? msg.substring(0, MAX_MSG_LENGTH) : msg;
    }
}
