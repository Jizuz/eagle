package com.eagle.admin.vo.request;

import lombok.Data;

/**
 * 配置分页查询请求
 */
@Data
public class ConfigQueryRequest {

    private String env;

    private String groupId;

    private String configKey;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
