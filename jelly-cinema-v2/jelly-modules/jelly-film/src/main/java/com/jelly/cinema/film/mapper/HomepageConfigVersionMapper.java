package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 首页配置版本 Mapper
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Mapper
public interface HomepageConfigVersionMapper extends BaseMapper<HomepageConfigVersion> {

    /**
     * 获取最新发布版本
     */
    @Select("SELECT * FROM t_homepage_config_version WHERE category = #{category} AND status = 'published' ORDER BY published_at DESC LIMIT 1")
    HomepageConfigVersion selectLatestPublished(@Param("category") String category);

    /**
     * 将某个分类的所有已发布版本归档
     */
    @Update("UPDATE t_homepage_config_version SET status = 'archived' WHERE category = #{category} AND status = 'published' AND id != #{excludeId}")
    int archivePreviousCategoryVersions(@Param("category") String category, @Param("excludeId") Long excludeId);
}
