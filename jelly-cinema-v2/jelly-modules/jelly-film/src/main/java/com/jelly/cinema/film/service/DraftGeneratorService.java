package com.jelly.cinema.film.service;

import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;

/**
 * 草案生成服务
 *
 * @author Jelly Cinema
 * @since 2026
 */
public interface DraftGeneratorService {

    /**
     * 生成并保存草案
     *
     * @param category 分类 (movie/tv_series...)
     * @param createdBy 创建人
     * @return 新生成的配置版本
     */
    HomepageConfigVersion generateDraft(String category, String createdBy);
}
