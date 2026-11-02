package com.eagle.admin.vo.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本历史视图
 */
@Data
public class HistoryVO {

    private Long id;

    private Long configId;

    private String configKey;

    private String configValue;

    private String valueType;

    private Integer itemVersion;

    /** UPDATE / DELETE */
    private String operation;

    private String operator;

    private LocalDateTime createTime;
}
