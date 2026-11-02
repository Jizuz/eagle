package com.eagle.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布记录表 t_config_publish: 记录每一次发布状态，用于追踪发布结果、失败重试
 */
@Data
@TableName("t_config_publish")
public class ConfigPublish {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String env;

    private String groupId;

    private String configKey;

    /** 关联t_config_history版本快照ID */
    private Long configHistoryId;

    /** 0待发布 1成功 2失败 */
    private Integer publishStatus;

    /** 失败信息 */
    private String publishMsg;

    private LocalDateTime createTime;
}
