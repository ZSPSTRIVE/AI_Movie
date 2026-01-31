package com.jelly.cinema.film.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;
import com.jelly.cinema.film.domain.entity.SlotRule;
import com.jelly.cinema.film.mapper.HomepageConfigVersionMapper;
import com.jelly.cinema.film.mapper.SlotRuleMapper;
import com.jelly.cinema.film.service.DraftGeneratorService;
import com.jelly.cinema.film.service.RecommendCandidateService;
import com.jelly.cinema.film.service.ResourceNormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 草案生成服务实现
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftGeneratorServiceImpl implements DraftGeneratorService {

    private final HomepageConfigVersionMapper configVersionMapper;
    private final SlotRuleMapper slotRuleMapper;
    private final RecommendCandidateService recommendCandidateService;
    private final ResourceNormalizationService normalizationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate(); // 可以注入Bean

    @Override
    public HomepageConfigVersion generateDraft(String category, String createdBy) {
        log.info("开始生成草案: category={}", category);

        // 1. 获取坑位规则
        List<SlotRule> slotRules = slotRuleMapper.selectByCategory(category);
        if (slotRules.isEmpty()) {
            throw new RuntimeException("该分类没有坑位规则配置");
        }

        // 2. 获取资源池 (这里暂时只从TVBox获取，后续可以合并更多来源)
        List<Map<String, Object>> normalizedPool = fetchAndNormalizePool();

        // 3. 为每个坑位生成内容
        List<Map<String, Object>> slotsConfig = new ArrayList<>();
        Set<String> usedCanonicalIds = new HashSet<>();

        for (SlotRule rule : slotRules) {
            Map<String, Object> slotConfig = new HashMap<>();
            slotConfig.put("slot_id", rule.getSlotId());
            slotConfig.put("section_type", rule.getSectionType());
            slotConfig.put("position", rule.getPosition());
            slotConfig.put("locked", rule.getLocked());

            // 生成候选
            List<Map<String, Object>> candidates = recommendCandidateService.generateCandidates(rule, normalizedPool);
            
            // 选出最佳且未使用的候选 (简单贪心)
            Map<String, Object> bestCandidate = null;
            for (Map<String, Object> candidate : candidates) {
                String id = (String) candidate.get("canonical_id");
                if (!usedCanonicalIds.contains(id)) {
                    bestCandidate = candidate;
                    usedCanonicalIds.add(id);
                    break;
                }
            }
            
            if (bestCandidate != null) {
                slotConfig.put("content", bestCandidate);
                slotConfig.put("status", "filled");
            } else {
                slotConfig.put("status", "empty"); // 需要补位
            }
            
            slotsConfig.add(slotConfig);
        }

        // 4. 保存为 Draft
        try {
            HomepageConfigVersion version = new HomepageConfigVersion();
            version.setCategory(category);
            version.setVersion(generateNextVersion(category));
            version.setStatus("draft");
            version.setConfigJson(objectMapper.writeValueAsString(slotsConfig));
            version.setCreatedBy(createdBy);
            version.setPublishNote("Generated draft");
            
            // 计算 Checksum
            // version.setChecksum(...) 
            
            configVersionMapper.insert(version);
            return version;
            
        } catch (Exception e) {
            throw new RuntimeException("保存草案失败", e);
        }
    }

    private List<Map<String, Object>> fetchAndNormalizePool() {
        // 模拟从TVBox获取
        try {
            String url = "http://localhost:3001/api/tvbox/recommend?limit=100";
            Map response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.get("data") instanceof List) {
                return normalizationService.normalize((List<Map<String, Object>>) response.get("data"));
            }
        } catch (Exception e) {
            log.warn("获取TVBox数据失败", e);
        }
        return Collections.emptyList();
    }
    
    private String generateNextVersion(String category) {
        // 简单生成版本号逻辑
        HomepageConfigVersion latest = configVersionMapper.selectLatestPublished(category);
        if (latest == null) {
            return "v1.0.0";
        }
        // 解析版本号并+1... 简化处理
        return "v" + System.currentTimeMillis(); 
    }
}
