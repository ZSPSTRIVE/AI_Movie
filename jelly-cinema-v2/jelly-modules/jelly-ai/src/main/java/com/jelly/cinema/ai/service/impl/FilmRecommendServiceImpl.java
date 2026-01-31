package com.jelly.cinema.ai.service.impl;

import com.jelly.cinema.ai.service.FilmRecommendService;
import com.jelly.cinema.ai.service.RagService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 影片AI推荐服务实现
 * 基于LangChain4j和RAG实现智能推荐
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilmRecommendServiceImpl implements FilmRecommendService {

    private final ChatLanguageModel chatLanguageModel;
    private final RagService ragService;

    // 推荐评分权重配置
    private static final double WEIGHT_RATING = 0.25;      // 原始评分权重
    private static final double WEIGHT_RECENCY = 0.20;     // 时效性权重
    private static final double WEIGHT_POPULARITY = 0.30;  // 热度权重
    private static final double WEIGHT_QUALITY = 0.25;     // 内容质量权重

    @Override
    public List<Map<String, Object>> analyzeFilms(List<Map<String, Object>> films) {
        log.info("开始AI分析 {} 部影片", films.size());

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> film : films) {
            try {
                Map<String, Object> analyzed = analyzeFilm(film);
                results.add(analyzed);
            } catch (Exception e) {
                log.warn("分析影片失败: {}", film.get("title"), e);
                // 失败时使用默认分数
                film.put("aiScore", calculateBaseScore(film));
                film.put("aiReason", "基于评分和年份推荐");
                results.add(film);
            }
        }

        // 按AI分数排序
        results.sort((a, b) -> {
            Double scoreA = getDoubleValue(a.get("aiScore"));
            Double scoreB = getDoubleValue(b.get("aiScore"));
            return scoreB.compareTo(scoreA);
        });

        log.info("AI分析完成，共处理 {} 部影片", results.size());
        return results;
    }

    @Override
    public List<Map<String, Object>> getBestRecommendations(String category, int limit) {
        log.info("获取AI精选推荐: category={}, limit={}", category, limit);
        // 这个方法需要从数据库获取影片列表，然后调用analyzeFilms
        // 实际实现会在HomepageContentService中调用
        return Collections.emptyList();
    }

    @Override
    public String generateRecommendReason(Map<String, Object> filmInfo) {
        String title = (String) filmInfo.getOrDefault("title", "未知影片");
        String description = (String) filmInfo.getOrDefault("description", "");
        String actors = (String) filmInfo.getOrDefault("actors", "");
        String director = (String) filmInfo.getOrDefault("director", "");
        Integer year = (Integer) filmInfo.get("year");

        // 尝试使用RAG获取相关上下文
        String ragContext = "";
        try {
            ragContext = ragService.retrieve(title + " " + description, 2);
        } catch (Exception e) {
            log.debug("RAG检索失败: {}", e.getMessage());
        }

        // 构建AI提示
        String prompt = buildRecommendPrompt(title, description, actors, director, year, ragContext);

        try {
            String response = chatLanguageModel.generate(prompt);
            return cleanResponse(response);
        } catch (Exception e) {
            log.warn("AI生成推荐理由失败: {}", e.getMessage());
            return generateFallbackReason(filmInfo);
        }
    }

    /**
     * 分析单个影片
     */
    private Map<String, Object> analyzeFilm(Map<String, Object> film) {
        Map<String, Object> result = new HashMap<>(film);

        // 计算多维度分数
        double ratingScore = calculateRatingScore(film);
        double recencyScore = calculateRecencyScore(film);
        double popularityScore = calculatePopularityScore(film);
        double qualityScore = calculateQualityScore(film);

        // 加权计算总分 (0-100)
        double totalScore = (ratingScore * WEIGHT_RATING +
                recencyScore * WEIGHT_RECENCY +
                popularityScore * WEIGHT_POPULARITY +
                qualityScore * WEIGHT_QUALITY) * 100;

        totalScore = Math.min(100, Math.max(0, totalScore));
        BigDecimal aiScore = BigDecimal.valueOf(totalScore).setScale(1, RoundingMode.HALF_UP);

        result.put("aiScore", aiScore.doubleValue());

        // 生成推荐理由
        String reason = generateRecommendReason(film);
        result.put("aiReason", reason);

        // 判断是否为AI精选 (分数 >= 75)
        result.put("aiBest", totalScore >= 75 ? 1 : 0);

        return result;
    }

    /**
     * 计算评分维度分数 (0-1)
     */
    private double calculateRatingScore(Map<String, Object> film) {
        Object ratingObj = film.get("rating");
        double rating = getDoubleValue(ratingObj);
        // 假设评分范围是0-10，转换为0-1
        return Math.min(1.0, rating / 10.0);
    }

    /**
     * 计算时效性分数 (0-1)
     * 越新的影片分数越高
     */
    private double calculateRecencyScore(Map<String, Object> film) {
        Object yearObj = film.get("year");
        if (yearObj == null) return 0.5;

        int year = (yearObj instanceof Number) ? ((Number) yearObj).intValue() : 2020;
        int currentYear = LocalDate.now().getYear();
        int age = currentYear - year;

        if (age <= 0) return 1.0;      // 当年新片
        if (age <= 1) return 0.9;      // 去年
        if (age <= 2) return 0.75;     // 两年内
        if (age <= 5) return 0.5;      // 五年内
        return 0.3;                     // 更老的影片
    }

    /**
     * 计算热度分数 (0-1)
     * 基于来源、标题热词等
     */
    private double calculatePopularityScore(Map<String, Object> film) {
        double score = 0.5; // 基础分

        String title = (String) film.getOrDefault("title", "");
        String description = (String) film.getOrDefault("description", "");
        String sourceName = (String) film.getOrDefault("sourceName", "");

        // 热门关键词加分
        String[] hotKeywords = {"热播", "独播", "首播", "高分", "必看", "推荐", "经典", "神作"};
        for (String keyword : hotKeywords) {
            if (title.contains(keyword) || description.contains(keyword)) {
                score += 0.1;
            }
        }

        // 优质来源加分
        String[] qualitySources = {"量子", "非凡", "光速", "红牛"};
        for (String source : qualitySources) {
            if (sourceName.contains(source)) {
                score += 0.1;
                break;
            }
        }

        return Math.min(1.0, score);
    }

    /**
     * 计算内容质量分数 (0-1)
     * 基于描述完整度、演员导演信息等
     */
    private double calculateQualityScore(Map<String, Object> film) {
        double score = 0.3; // 基础分

        String description = (String) film.getOrDefault("description", "");
        String actors = (String) film.getOrDefault("actors", "");
        String director = (String) film.getOrDefault("director", "");
        String region = (String) film.getOrDefault("region", "");

        // 描述完整度
        if (description != null && description.length() > 50) score += 0.2;
        if (description != null && description.length() > 100) score += 0.1;

        // 演职人员信息完整度
        if (actors != null && !actors.isEmpty()) score += 0.15;
        if (director != null && !director.isEmpty()) score += 0.1;

        // 地区信息
        if (region != null && !region.isEmpty()) score += 0.05;

        return Math.min(1.0, score);
    }

    /**
     * 计算基础分数（降级方案）
     */
    private double calculateBaseScore(Map<String, Object> film) {
        double rating = getDoubleValue(film.get("rating"));
        return Math.min(100, Math.max(0, rating * 10));
    }

    /**
     * 构建推荐理由生成提示
     */
    private String buildRecommendPrompt(String title, String description, String actors,
                                         String director, Integer year, String ragContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下影片生成一句简洁的推荐理由（不超过30字）：\n\n");
        prompt.append("影片名称：").append(title).append("\n");
        if (year != null) prompt.append("年份：").append(year).append("\n");
        if (description != null && !description.isEmpty()) {
            prompt.append("简介：").append(description.length() > 100 ?
                    description.substring(0, 100) + "..." : description).append("\n");
        }
        if (actors != null && !actors.isEmpty()) {
            prompt.append("主演：").append(actors).append("\n");
        }
        if (director != null && !director.isEmpty()) {
            prompt.append("导演：").append(director).append("\n");
        }
        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("\n相关背景：").append(ragContext.length() > 200 ?
                    ragContext.substring(0, 200) : ragContext).append("\n");
        }
        prompt.append("\n请直接输出推荐理由，不要包含其他内容：");
        return prompt.toString();
    }

    /**
     * 清理AI返回的响应
     */
    private String cleanResponse(String response) {
        if (response == null) return "";
        // 移除可能的引号、换行等
        return response.trim()
                .replaceAll("^[\"']|[\"']$", "")
                .replaceAll("\n", " ")
                .replaceAll("\\s+", " ");
    }

    /**
     * 生成降级推荐理由
     */
    private String generateFallbackReason(Map<String, Object> film) {
        String region = (String) film.getOrDefault("region", "");
        Integer year = (Integer) film.get("year");
        Double rating = getDoubleValue(film.get("rating"));

        StringBuilder reason = new StringBuilder();
        if (year != null && year >= LocalDate.now().getYear()) {
            reason.append("新片上映");
        } else if (rating >= 8.0) {
            reason.append("高分佳作");
        } else if (rating >= 7.0) {
            reason.append("口碑之选");
        } else {
            reason.append("精彩推荐");
        }

        if (region != null && !region.isEmpty()) {
            if (region.contains("中国") || region.contains("大陆") || region.contains("内地")) {
                reason.append("，国产精品");
            } else if (region.contains("韩") || region.contains("日")) {
                reason.append("，亚洲热门");
            } else if (region.contains("美") || region.contains("欧")) {
                reason.append("，欧美大片");
            }
        }

        return reason.toString();
    }

    private Double getDoubleValue(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
