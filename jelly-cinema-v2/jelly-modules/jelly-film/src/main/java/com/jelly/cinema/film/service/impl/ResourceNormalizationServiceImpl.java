package com.jelly.cinema.film.service.impl;

import com.jelly.cinema.film.service.ResourceNormalizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源归一化服务实现
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service
public class ResourceNormalizationServiceImpl implements ResourceNormalizationService {

    @Override
    public List<Map<String, Object>> normalize(List<Map<String, Object>> sourceData) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sourceData == null || sourceData.isEmpty()) {
            return result;
        }

        for (Map<String, Object> item : sourceData) {
            try {
                Map<String, Object> normalized = new HashMap<>();
                
                // 基础字段归一
                normalized.put("canonical_id", item.get("id")); // 暂时使用TVBox ID作为唯一标识
                normalized.put("tvbox_id", item.get("id"));
                normalized.put("title", item.getOrDefault("title", "未知标题"));
                normalized.put("cover_url", item.get("coverUrl"));
                normalized.put("description", item.getOrDefault("description", ""));
                
                // 评分归一
                Object ratingObj = item.get("rating");
                BigDecimal rating = BigDecimal.ZERO;
                if (ratingObj instanceof Number) {
                    rating = new BigDecimal(ratingObj.toString());
                } else if (ratingObj instanceof String) {
                    try {
                        rating = new BigDecimal((String) ratingObj);
                    } catch (Exception ignored) {}
                }
                normalized.put("rating", rating);
                
                // 其他字段
                normalized.put("year", item.get("year"));
                normalized.put("region", item.get("region"));
                normalized.put("source_name", item.get("sourceName"));
                normalized.put("actors", item.get("actors"));
                normalized.put("director", item.get("director"));
                
                // 是否缺少关键字段标识
                List<String> missingFields = new ArrayList<>();
                if (item.get("playUrl") == null && item.get("vod_play_url") == null) {
                    // TVBox数据通常不直接含播放地址，需二次解析，这里先标记
                    // missingFields.add("play_url"); 
                }
                normalized.put("missing_fields", missingFields);

                result.add(normalized);
            } catch (Exception e) {
                log.warn("资源归一化失败: {}", item, e);
            }
        }
        
        return result;
    }
}
