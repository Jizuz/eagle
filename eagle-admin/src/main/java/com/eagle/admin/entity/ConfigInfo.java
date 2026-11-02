package com.eagle.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置主表 t_config_info
 */
@Data
@TableName("t_config_info")
public class ConfigInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String env;

    private String groupId;

    private String configKey;

    private String configValue;

    private String valueType;

    private String description;

    /** 软删除标记: 0正常 1已删除 */
    @TableLogic
    private Integer isDeleted;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
