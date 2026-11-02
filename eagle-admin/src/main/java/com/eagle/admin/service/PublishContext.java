package com.eagle.admin.service;

import com.eagle.common.dto.ConfigNodeDTO;
import com.eagle.common.enums.OperationType;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发布上下文: 数据库事务内组装，事务提交后用于ZK同步
 */
@Data
@AllArgsConstructor
public class PublishContext {

    private Long configId;

    private Long publishId;

    private String env;

    private String groupId;

    private String configKey;

    private OperationType operation;

    /** 删除操作时为null */
    private ConfigNodeDTO node;
}
