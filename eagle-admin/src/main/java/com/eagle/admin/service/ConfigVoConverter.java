package com.eagle.admin.service;

import com.eagle.admin.entity.ConfigHistory;
import com.eagle.admin.entity.ConfigInfo;
import com.eagle.admin.entity.ConfigPublish;
import com.eagle.admin.mapper.ConfigHistoryMapper;
import com.eagle.admin.mapper.ConfigPublishMapper;
import com.eagle.admin.vo.response.ConfigVO;
import com.eagle.admin.vo.response.HistoryVO;
import com.eagle.admin.vo.response.PublishVO;
import com.eagle.common.enums.PublishStatus;
import org.springframework.stereotype.Component;

/**
 * 实体 -> 视图对象转换器（含版本号、最近发布状态等附加信息组装）
 */
@Component
public class ConfigVoConverter {

    private final ConfigHistoryMapper configHistoryMapper;
    private final ConfigPublishMapper configPublishMapper;

    public ConfigVoConverter(ConfigHistoryMapper configHistoryMapper, ConfigPublishMapper configPublishMapper) {
        this.configHistoryMapper = configHistoryMapper;
        this.configPublishMapper = configPublishMapper;
    }

    /**
     * 完整组装: 最新版本号 + 最近一次发布状态
     */
    public ConfigVO toVO(ConfigInfo info) {
        if (info == null) {
            return null;
        }
        ConfigPublish lastPublish = configPublishMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConfigPublish>()
                        .eq(ConfigPublish::getEnv, info.getEnv())
                        .eq(ConfigPublish::getGroupId, info.getGroupId())
                        .eq(ConfigPublish::getConfigKey, info.getConfigKey())
                        .orderByDesc(ConfigPublish::getId)
                        .last("LIMIT 1"));
        return toVO(info, lastPublish == null ? null : lastPublish.getId());
    }

    /**
     * 指定发布记录组装（发布操作返回时使用）
     */
    public ConfigVO toVO(ConfigInfo info, Long publishId) {
        if (info == null) {
            return null;
        }
        ConfigVO vo = new ConfigVO();
        vo.setId(info.getId());
        vo.setEnv(info.getEnv());
        vo.setGroupId(info.getGroupId());
        vo.setConfigKey(info.getConfigKey());
        vo.setConfigValue(info.getConfigValue());
        vo.setValueType(info.getValueType());
        vo.setDescription(info.getDescription());
        vo.setCreatedBy(info.getCreatedBy());
        vo.setUpdatedBy(info.getUpdatedBy());
        vo.setCreateTime(info.getCreateTime());
        vo.setUpdateTime(info.getUpdateTime());
        vo.setLatestVersion(configHistoryMapper.selectMaxVersion(info.getId()));
        if (publishId != null) {
            ConfigPublish publish = configPublishMapper.selectById(publishId);
            if (publish != null) {
                vo.setLastPublishStatus(publish.getPublishStatus());
                vo.setLastPublishStatusDesc(descOf(publish.getPublishStatus()));
                vo.setLastPublishMsg(publish.getPublishMsg());
            }
        }
        return vo;
    }

    public HistoryVO toHistoryVO(ConfigHistory history) {
        HistoryVO vo = new HistoryVO();
        vo.setId(history.getId());
        vo.setConfigId(history.getConfigId());
        vo.setConfigKey(history.getConfigKey());
        vo.setConfigValue(history.getConfigValue());
        vo.setValueType(history.getValueType());
        vo.setItemVersion(history.getItemVersion());
        vo.setOperation(history.getOperation());
        vo.setOperator(history.getOperator());
        vo.setCreateTime(history.getCreateTime());
        return vo;
    }

    public PublishVO toPublishVO(ConfigPublish publish, ConfigHistory history) {
        PublishVO vo = new PublishVO();
        vo.setId(publish.getId());
        vo.setEnv(publish.getEnv());
        vo.setGroupId(publish.getGroupId());
        vo.setConfigKey(publish.getConfigKey());
        vo.setConfigHistoryId(publish.getConfigHistoryId());
        vo.setPublishStatus(publish.getPublishStatus());
        vo.setPublishStatusDesc(descOf(publish.getPublishStatus()));
        vo.setPublishMsg(publish.getPublishMsg());
        vo.setCreateTime(publish.getCreateTime());
        if (history != null) {
            vo.setOperation(history.getOperation());
            vo.setItemVersion(history.getItemVersion());
        }
        return vo;
    }

    private String descOf(Integer status) {
        PublishStatus publishStatus = PublishStatus.of(status);
        return publishStatus == null ? String.valueOf(status) : publishStatus.getDesc();
    }
}
