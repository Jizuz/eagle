package com.eagle.admin.vo.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布记录视图
 */
@Data
public class PublishVO {

    private Long id;

    private String env;

    private String groupId;

    private String configKey;

    private Long configHistoryId;

    /** UPDATE / DELETE */
    private String operation;

    private Integer itemVersion;

    /** 0待发布 1成功 2失败 */
    private Integer publishStatus;

    private String publishStatusDesc;

    private String publishMsg;

    private LocalDateTime createTime;
}
