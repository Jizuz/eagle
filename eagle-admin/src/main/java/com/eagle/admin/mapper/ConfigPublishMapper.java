package com.eagle.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eagle.admin.entity.ConfigPublish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 发布记录表 Mapper
 */
@Mapper
public interface ConfigPublishMapper extends BaseMapper<ConfigPublish> {

    /**
     * 更新发布状态与失败信息
     */
    @Update("UPDATE t_config_publish SET publish_status = #{status}, publish_msg = #{msg} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("msg") String msg);
}
