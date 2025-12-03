package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.admin.domain.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 敏感词 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

    /**
     * 获取所有启用的敏感词
     */
    @Select("SELECT word, strategy FROM t_sensitive_word WHERE status = 1")
    List<SensitiveWord> selectEnabledWords();
}
