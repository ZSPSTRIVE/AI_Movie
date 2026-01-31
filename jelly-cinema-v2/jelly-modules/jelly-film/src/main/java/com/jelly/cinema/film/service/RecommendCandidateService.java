package com.jelly.cinema.film.service;

import com.jelly.cinema.film.domain.entity.SlotRule;

import java.util.List;
import java.util.Map;

/**
 * 候选推荐服务
 * 负责为特定坑位生成候选推荐列表
 *
 * @author Jelly Cinema
 * @since 2026
 */
public interface RecommendCandidateService {

    /**
     * 为指定坑位生成候选推荐
     *
     * @param slotRule 坑位规则
     * @param sourcePool 资源池（已归一化）
     * @return 候选列表（按分数排序）
     */
    List<Map<String, Object>> generateCandidates(SlotRule slotRule, List<Map<String, Object>> sourcePool);
}
