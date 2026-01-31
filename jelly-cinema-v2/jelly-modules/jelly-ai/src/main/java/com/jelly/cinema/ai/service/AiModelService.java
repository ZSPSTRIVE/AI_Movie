package com.jelly.cinema.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI大模型服务 - 用于首页内容智能排序
 * 使用项目已有的LangChain4j配置（支持SiliconFlow/DeepSeek故障转移）
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    /**
     * 调用大模型API
     */
    public String chat(String systemPrompt, String userMessage) {
        try {
            String fullPrompt = systemPrompt + "\n\n用户问题：" + userMessage;
            log.info("调用AI模型进行分析...");
            
            String response = chatLanguageModel.generate(fullPrompt);
            log.info("AI响应长度: {}", response != null ? response.length() : 0);
            
            return response;
        } catch (Exception e) {
            log.error("AI调用失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 分析电影数据并返回AI推荐分数
     */
    public Map<String, Object> analyzeFilm(String title, String description, Integer year, 
                                            String actors, String director, String region) {
        String systemPrompt = """
                你是一位专业的电影推荐专家。请根据以下电影信息，分析这部电影/电视剧在当前时间点（2026年1月）的主流程度和推荐价值。
                
                评分标准：
                1. 时效性：近期上映的作品（2023-2026）得分高
                2. 热门度：知名演员、导演、话题性
                3. 口碑：根据题材和描述判断质量
                4. 受众：受众面广的作品得分高
                
                请直接返回JSON格式（不要有其他文字）：
                {"score": 85.5, "reason": "推荐理由，50字以内"}
                """;

        String userMessage = String.format("""
                电影名：%s
                简介：%s
                年份：%s
                主演：%s
                导演：%s
                地区：%s
                """, 
                title != null ? title : "未知",
                description != null ? description : "暂无简介",
                year != null ? year : "未知",
                actors != null ? actors : "未知",
                director != null ? director : "未知",
                region != null ? region : "未知");

        String response = chat(systemPrompt, userMessage);
        if (response == null) {
            return createDefaultResult();
        }

        try {
            // 提取JSON部分
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}") + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, jsonEnd);
                JsonNode node = objectMapper.readTree(jsonStr);
                
                Map<String, Object> result = new HashMap<>();
                result.put("score", node.path("score").asDouble(50.0));
                result.put("reason", node.path("reason").asText("AI分析完成"));
                return result;
            }
        } catch (Exception e) {
            log.error("解析AI响应失败: {}", e.getMessage());
        }

        return createDefaultResult();
    }

    private Map<String, Object> createDefaultResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("score", 50.0);
        result.put("reason", "默认推荐");
        return result;
    }

    /**
     * 批量分析电影列表并排序
     */
    public List<Map<String, Object>> batchAnalyzeAndSort(List<Map<String, Object>> films) {
        log.info("开始AI批量分析，电影数量: {}", films.size());

        // 限制批量分析数量，避免API超时
        int maxAnalyze = Math.min(films.size(), 20);
        
        for (int i = 0; i < maxAnalyze; i++) {
            Map<String, Object> film = films.get(i);
            try {
                Map<String, Object> analysis = analyzeFilm(
                        (String) film.get("title"),
                        (String) film.get("description"),
                        film.get("year") instanceof Integer ? (Integer) film.get("year") : null,
                        (String) film.get("actors"),
                        (String) film.get("director"),
                        (String) film.get("region")
                );

                film.put("aiScore", analysis.get("score"));
                film.put("aiReason", analysis.get("reason"));

                // 避免API限流
                Thread.sleep(300);

            } catch (Exception e) {
                log.error("分析电影 {} 失败: {}", film.get("title"), e.getMessage());
                film.put("aiScore", 50.0);
                film.put("aiReason", "分析失败");
            }
        }
        
        // 未分析的电影给默认分数
        for (int i = maxAnalyze; i < films.size(); i++) {
            films.get(i).put("aiScore", 50.0);
            films.get(i).put("aiReason", "待分析");
        }

        // 按AI分数排序
        films.sort((a, b) -> {
            Double scoreA = (Double) a.getOrDefault("aiScore", 50.0);
            Double scoreB = (Double) b.getOrDefault("aiScore", 50.0);
            return scoreB.compareTo(scoreA);
        });

        log.info("AI批量分析完成");
        return films;
    }

    /**
     * 智能筛选推荐内容
     */
    public List<Map<String, Object>> intelligentFilter(List<Map<String, Object>> films, int targetCount) {
        String systemPrompt = """
                你是首页内容策展专家。从提供的电影列表中，选择最适合展示在首页的内容。
                
                选择标准：
                1. 优先选择2023-2026年的新作品
                2. 兼顾不同类型（动作、剧情、喜剧等）
                3. 包含知名演员/导演的作品
                4. 避免全部选择同一类型
                
                请直接返回JSON格式，selected是选中电影的索引数组：
                {"selected": [0, 3, 5, 7]}
                """;

        // 构建电影列表摘要
        StringBuilder filmList = new StringBuilder();
        int listSize = Math.min(films.size(), 30);
        for (int i = 0; i < listSize; i++) {
            Map<String, Object> film = films.get(i);
            filmList.append(String.format("%d. %s (%s)\n",
                    i, 
                    film.get("title"), 
                    film.get("year")));
        }

        String userMessage = String.format("从以下%d部电影中选择%d部最适合首页推荐：\n%s",
                listSize, targetCount, filmList.toString());

        String response = chat(systemPrompt, userMessage);
        if (response == null) {
            return films.subList(0, Math.min(targetCount, films.size()));
        }

        try {
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}") + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, jsonEnd);
                JsonNode node = objectMapper.readTree(jsonStr);
                JsonNode selected = node.path("selected");

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode idx : selected) {
                    int index = idx.asInt();
                    if (index >= 0 && index < films.size()) {
                        result.add(films.get(index));
                    }
                }

                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("解析筛选响应失败: {}", e.getMessage());
        }

        return films.subList(0, Math.min(targetCount, films.size()));
    }
}
