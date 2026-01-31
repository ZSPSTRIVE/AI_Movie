package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.SlotRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 坑位规则 Mapper
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Mapper
public interface SlotRuleMapper extends BaseMapper<SlotRule> {

    /**
     * 获取分类的所有坑位规则，按板块和位置排序
     */
    @Select("SELECT * FROM t_slot_rule WHERE category = #{category} ORDER BY section_type ASC, position ASC")
    List<SlotRule> selectByCategory(@Param("category") String category);

    /**
     * 获取特定板块的规则
     */
    @Select("SELECT * FROM t_slot_rule WHERE category = #{category} AND section_type = #{sectionType} ORDER BY position ASC")
    List<SlotRule> selectBySection(@Param("category") String category, @Param("sectionType") String sectionType);
}
