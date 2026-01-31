package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.PublishedContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 已发布内容快照 Mapper
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Mapper
public interface PublishedContentMapper extends BaseMapper<PublishedContent> {

    /**
     * 获取前台展示的已发布内容
     */
    @Select("SELECT * FROM t_published_content WHERE category = #{category} AND config_version = #{version} AND status = 1 ORDER BY position ASC")
    List<PublishedContent> selectFrontendList(@Param("category") String category, @Param("version") String version);

    /**
     * 删除旧版本的快照数据
     */
    @Select("DELETE FROM t_published_content WHERE category = #{category} AND config_version != #{currentVersion}")
    void deleteOldVersions(@Param("category") String category, @Param("currentVersion") String currentVersion);
}
