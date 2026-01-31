package com.jelly.cinema.film.service;

import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;

/**
 * 首页配置服务
 *
 * @author Jelly Cinema
 * @since 2026
 */
public interface HomepageConfigService {

    /**
     * 发布配置版本
     * @param versionId 版本ID
     */
    void publish(Long versionId);

    /**
     * 回滚到指定版本
     * @param versionId 目标版本ID
     */
    void rollback(Long versionId);
}
