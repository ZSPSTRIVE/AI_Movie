package com.jelly.cinema.ai.service;

import java.util.List;
import java.util.Map;

/**
 * 影片AI推荐服务
 * 结合RAG和LangChain4j实现智能推荐
 *
 * @author Jelly Cinema
 * @since 2026
 */
public interface FilmRecommendService {

    /**
     * 批量分析影片，生成AI推荐评分和理由
     *
     * @param films 影片信息列表
     * @return 分析结果列表，包含aiScore和aiReason
     */
    List<Map<String, Object>> analyzeFilms(List<Map<String, Object>> films);

    /**
     * 获取AI精选推荐
     *
     * @param category 分类（movie/tv_series）
     * @param limit    数量限制
     * @return AI精选影片列表
     */
    List<Map<String, Object>> getBestRecommendations(String category, int limit);

    /**
     * 为单个影片生成推荐理由
     *
     * @param filmInfo 影片信息
     * @return 推荐理由
     */
    String generateRecommendReason(Map<String, Object> filmInfo);
}
