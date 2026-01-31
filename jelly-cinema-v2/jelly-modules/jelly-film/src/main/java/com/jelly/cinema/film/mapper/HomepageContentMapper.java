package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.HomepageContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 首页内容 Mapper
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Mapper
public interface HomepageContentMapper extends BaseMapper<HomepageContent> {

    /**
     * 根据板块类型获取启用的内容列表
     */
    @Select("SELECT * FROM t_homepage_content WHERE section_type = #{sectionType} AND status = 1 AND deleted = 0 ORDER BY sort_order ASC LIMIT #{limit}")
    List<HomepageContent> selectBySectionType(@Param("sectionType") String sectionType, @Param("limit") Integer limit);

    /**
     * 根据内容类型获取启用的内容列表
     */
    @Select("SELECT * FROM t_homepage_content WHERE content_type = #{contentType} AND status = 1 AND deleted = 0 ORDER BY sort_order ASC LIMIT #{limit}")
    List<HomepageContent> selectByContentType(@Param("contentType") String contentType, @Param("limit") Integer limit);

    /**
     * 获取首页推荐内容
     */
    @Select("SELECT * FROM t_homepage_content WHERE section_type = 'recommend' AND status = 1 AND deleted = 0 ORDER BY ai_score DESC, sort_order ASC LIMIT #{limit}")
    List<HomepageContent> selectRecommendList(@Param("limit") Integer limit);
}
