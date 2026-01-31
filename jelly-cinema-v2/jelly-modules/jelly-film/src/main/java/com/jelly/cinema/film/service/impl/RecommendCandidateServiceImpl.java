package com.jelly.cinema.film.service.impl;

import com.jelly.cinema.film.domain.entity.SlotRule;
import com.jelly.cinema.film.service.RecommendCandidateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 候选推荐服务实现
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service
public class RecommendCandidateServiceImpl implements RecommendCandidateService {

    @Override
    public List<Map<String, Object>> generateCandidates(SlotRule slotRule, List<Map<String, Object>> sourcePool) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        
        for (Map<String, Object> item : sourcePool) {
            // 1. 基础过滤（评分、地区等）
            if (!checkBasicRules(slotRule, item)) {
                continue;
            }
            
            // 2. 计算匹配分数
            double score = calculateScore(slotRule, item);
            item.put("match_score", score);
            
            candidates.add(item);
        }
        
        // 3. 排序：分数降序 -> 年份降序 -> ID升序（确定性）
        return candidates.stream()
            .sorted(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("match_score")).reversed()
                .thenComparing((m) -> (Integer) m.getOrDefault("year", 0), Comparator.reverseOrder())
                .thenComparing((m) -> (String) m.getOrDefault("tvbox_id", ""), Comparator.naturalOrder()))
            .collect(Collectors.toList());
    }

    private boolean checkBasicRules(SlotRule rule, Map<String, Object> item) {
        // 评分过滤
        BigDecimal rating = (BigDecimal) item.getOrDefault("rating", BigDecimal.ZERO);
        if (rule.getMinRating() != null && rating.compareTo(rule.getMinRating()) < 0) {
            return false;
        }
        
        // 类型过滤 (简化逻辑)
        // if (rule.getPreferredGenres() != null) ...
        
        return true;
    }

    private double calculateScore(SlotRule rule, Map<String, Object> item) {
        double score = 0.0;
        
        // 基础分：评分
        BigDecimal rating = (BigDecimal) item.getOrDefault("rating", BigDecimal.ZERO);
        score += rating.doubleValue() * 10;
        
        // 年份加权
        Integer year = (Integer) item.get("year");
        if (year != null) {
            int currentYear = 2026; // 假定当前年份
            if (year >= currentYear - 1) score += 50;
            else if (year >= currentYear - 3) score += 30;
        }
        
        return score;
    }
}
