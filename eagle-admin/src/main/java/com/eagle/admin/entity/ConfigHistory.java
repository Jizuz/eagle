package com.eagle.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本历史表 t_config_history: 同一配置项独立递增版本号，每次发布保存快照
 */
@Data
@TableName("t_config_history")
public class ConfigHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;

    private String env;

    private String groupId;

    private String configKey;

    private String configValue;

    private String valueType;

    /** 当前配置项版本号，同config_id下自增 */
    private Integer itemVersion;

    /** 操作类型: UPDATE / DELETE */
    private String operation;

    private String operator;

    private LocalDateTime createTime;
}
