package com.jelly.cinema.film.domain.dto;

import lombok.Data;

/**
 * 从片库导入首页内容的请求参数。
 */
@Data
public class HomepageFilmImportDTO {

    /**
     * 电影库 ID。
     */
    private Long filmId;

    /**
     * 首页板块类型：recommend/hot/new/trending。
     */
    private String sectionType;

    /**
     * 首页内容类型：movie/tv_series/variety/anime。
     */
    private String contentType;

    /**
     * 指定排序位；为空时自动追加到末尾。
     */
    private Integer sortOrder;

    /**
     * 是否替换同板块同排序位的现有资源。
     */
    private Boolean replaceExisting;
}
