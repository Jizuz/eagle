package com.eagle.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eagle.admin.entity.ConfigInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 配置主表 Mapper
 */
@Mapper
public interface ConfigInfoMapper extends BaseMapper<ConfigInfo> {

    /**
     * 查询包含软删除记录的配置（绕过逻辑删除过滤）。
     * 用途: 新增配置时若唯一键(env, group_id, config_key)已被软删行占用，
     * 需复用该行并恢复，避免直接insert触发唯一键冲突。
     */
    @InterceptorIgnore
    @Select("SELECT * FROM t_config_info WHERE env = #{env} AND group_id = #{groupId} AND config_key = #{configKey}")
    ConfigInfo selectIncludeDeleted(@Param("env") String env,
                                    @Param("groupId") String groupId,
                                    @Param("configKey") String configKey);

    /**
     * 恢复软删除行并更新配置内容
     */
    @InterceptorIgnore
    @Update("UPDATE t_config_info SET config_value = #{info.configValue}, value_type = #{info.valueType}, " +
            "description = #{info.description}, updated_by = #{info.updatedBy}, is_deleted = 0 " +
            "WHERE id = #{info.id}")
    int reviveDeleted(@Param("info") ConfigInfo info);
}
