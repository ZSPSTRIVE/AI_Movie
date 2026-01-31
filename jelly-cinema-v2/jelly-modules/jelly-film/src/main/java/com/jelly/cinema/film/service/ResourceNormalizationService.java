package com.jelly.cinema.film.service;

import java.util.List;
import java.util.Map;

/**
 * 资源归一化服务
 * 负责将多来源资源池归一化为统一格式
 *
 * @author Jelly Cinema
 * @since 2026
 */
public interface ResourceNormalizationService {

    /**
     * 归一化资源
     *
     * @param sourceData 原始数据
     * @return 归一化后的资源列表
     */
    List<Map<String, Object>> normalize(List<Map<String, Object>> sourceData);
}
