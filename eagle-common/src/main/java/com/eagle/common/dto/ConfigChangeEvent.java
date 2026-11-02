package com.eagle.common.dto;

import com.eagle.common.enums.ChangeType;
import lombok.Data;

/**
 * 配置变更事件
 */
@Data
public class ConfigChangeEvent {

    /** 变更的配置键 */
    private String key;

    /** 变更前节点(新增时为null) */
    private ConfigNodeDTO oldNode;

    /** 变更后节点(删除时为null) */
    private ConfigNodeDTO newNode;

    /** 变更类型 */
    private ChangeType changeType;
}
