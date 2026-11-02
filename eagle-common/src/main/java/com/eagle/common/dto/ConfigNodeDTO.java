package com.eagle.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZK节点存储的标准JSON结构: 类型和值一起下发，客户端无需回查数据库
 * <pre>
 * {"type":"boolean","value":"true"}
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigNodeDTO {

    /** 配置类型: string / number / boolean */
    private String type;

    /** 配置值(字符串形式) */
    private String value;
}
