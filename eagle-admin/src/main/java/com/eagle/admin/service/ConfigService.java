package com.eagle.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eagle.admin.config.EagleZkProperties;
import com.eagle.admin.entity.ConfigHistory;
import com.eagle.admin.entity.ConfigInfo;
import com.eagle.admin.mapper.ConfigHistoryMapper;
import com.eagle.admin.mapper.ConfigInfoMapper;
import com.eagle.admin.vo.request.ConfigQueryRequest;
import com.eagle.admin.vo.response.ConfigVO;
import com.eagle.admin.vo.response.HistoryVO;
import com.eagle.admin.vo.response.PageResult;
import com.eagle.admin.vo.response.PreviewVO;
import com.eagle.common.dto.ConfigNodeDTO;
import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 配置查询服务: 分页/详情/历史版本/发布预览（只读，不触发发布）
 */
@Service
public class ConfigService {

    private final ConfigInfoMapper configInfoMapper;
    private final ConfigHistoryMapper configHistoryMapper;
    private final ConfigVoConverter converter;
    private final EagleZkProperties zkProperties;
    private final ObjectMapper objectMapper;

    public ConfigService(ConfigInfoMapper configInfoMapper,
                         ConfigHistoryMapper configHistoryMapper,
                         ConfigVoConverter converter,
                         EagleZkProperties zkProperties,
                         ObjectMapper objectMapper) {
        this.configInfoMapper = configInfoMapper;
        this.configHistoryMapper = configHistoryMapper;
        this.converter = converter;
        this.zkProperties = zkProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 配置分页查询
     */
    public PageResult<ConfigVO> page(ConfigQueryRequest req) {
        Page<ConfigInfo> page = new Page<>(req.getPageNum(), req.getPageSize());
        LambdaQueryWrapper<ConfigInfo> wrapper = new LambdaQueryWrapper<ConfigInfo>()
                .eq(StringUtils.hasText(req.getEnv()), ConfigInfo::getEnv, req.getEnv())
                .eq(StringUtils.hasText(req.getGroupId()), ConfigInfo::getGroupId, req.getGroupId())
                .like(StringUtils.hasText(req.getConfigKey()), ConfigInfo::getConfigKey, req.getConfigKey())
                .orderByDesc(ConfigInfo::getUpdateTime);
        Page<ConfigInfo> result = configInfoMapper.selectPage(page, wrapper);
        List<ConfigVO> vos = result.getRecords().stream().map(converter::toVO).toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), vos);
    }

    /**
     * 配置详情
     */
    public ConfigVO detail(Long id) {
        return converter.toVO(requireConfig(id));
    }

    /**
     * 版本历史列表（按版本号倒序）
     */
    public List<HistoryVO> histories(Long id) {
        requireConfig(id);
        return configHistoryMapper.selectList(new LambdaQueryWrapper<ConfigHistory>()
                        .eq(ConfigHistory::getConfigId, id)
                        .orderByDesc(ConfigHistory::getItemVersion))
                .stream().map(converter::toHistoryVO).toList();
    }

    /**
     * 发布预览: 即将写入ZK的节点路径与JSON内容
     */
    public PreviewVO preview(Long id) {
        ConfigInfo info = requireConfig(id);
        PreviewVO vo = new PreviewVO();
        vo.setZkPath(zkProperties.getRoot() + "/" + info.getEnv() + "/" + info.getGroupId() + "/" + info.getConfigKey());
        try {
            vo.setContent(objectMapper.writeValueAsString(new ConfigNodeDTO(info.getValueType(), info.getConfigValue())));
        } catch (JsonProcessingException e) {
            throw new EagleException(ErrorCode.SYSTEM_ERROR, "预览JSON组装失败: " + e.getMessage());
        }
        return vo;
    }

    private ConfigInfo requireConfig(Long id) {
        ConfigInfo info = configInfoMapper.selectById(id);
        if (info == null) {
            throw new EagleException(ErrorCode.NOT_FOUND, "配置不存在或已删除: id=" + id);
        }
        return info;
    }
}
