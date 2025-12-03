package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.admin.domain.entity.AdminLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员操作日志 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface AdminLogMapper extends BaseMapper<AdminLog> {
}
