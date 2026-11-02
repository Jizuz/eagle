package com.eagle.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eagle.admin.entity.ConfigHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 版本历史表 Mapper
 */
@Mapper
public interface ConfigHistoryMapper extends BaseMapper<ConfigHistory> {

    /**
     * 查询指定配置当前最大版本号，无历史时返回0
     */
    @Select("SELECT COALESCE(MAX(item_version), 0) FROM t_config_history WHERE config_id = #{configId}")
    int selectMaxVersion(@Param("configId") Long configId);
}
