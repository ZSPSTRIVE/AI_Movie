package com.jelly.cinema.film.domain.dto;

import com.jelly.cinema.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 电影查询 DTO
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FilmQueryDTO extends PageQuery {

    /**
     * 搜索关键词
     */
    private String keyword;

    /**
     * 分类 ID
     */
    private Long categoryId;

    /**
     * 年份
     */
    private Integer year;

    /**
     * 地区
     */
    private String region;

    /**
     * 排序方式：hot(热门), new(最新), rating(评分)
     */
    private String sort = "hot";
}
