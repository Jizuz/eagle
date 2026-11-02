package com.eagle.admin.vo.request;

import lombok.Data;

/**
 * 发布记录分页查询请求
 */
@Data
public class PublishQueryRequest {

    private String env;

    private String groupId;

    private String configKey;

    /** 0待发布 1成功 2失败 */
    private Integer publishStatus;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
