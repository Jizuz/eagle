package com.eagle.admin.vo.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置详情视图
 */
@Data
public class ConfigVO {

    private Long id;

    private String env;

    private String groupId;

    private String configKey;

    private String configValue;

    private String valueType;

    private String description;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 当前版本号（最新历史快照版本） */
    private Integer latestVersion;

    /** 最近一次发布状态: 0待发布 1成功 2失败 */
    private Integer lastPublishStatus;

    private String lastPublishStatusDesc;

    private String lastPublishMsg;
}
