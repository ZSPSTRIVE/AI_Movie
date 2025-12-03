package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.admin.domain.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报记录 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
