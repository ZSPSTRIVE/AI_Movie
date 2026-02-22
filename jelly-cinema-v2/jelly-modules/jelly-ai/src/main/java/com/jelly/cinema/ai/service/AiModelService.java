package com.jelly.cinema.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI model service for homepage recommendation analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    private static final int STRUCTURED_RETRY_TIMES = 2;

    /**
     * Call LLM with system prompt + user message.
     */
    public String chat(String systemPrompt, String userMessage) {
        try {
            String fullPrompt = systemPrompt + "\n\n用户问题：" + userMessage;
            log.info("Calling AI model for analysis...");
            String response = chatLanguageModel.generate(fullPrompt);
            log.info("AI response length: {}", response != null ? response.length() : 0);
            return response;
        } catch (Exception e) {
            log.error("AI call failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Analyze a film and return score + reason.
     */
    public Map<String, Object> analyzeFilm(
            String title,
            String description,
            Integer year,
            String actors,
            String director,
            String region
    ) {
        String systemPrompt = """
                你是一位专业电影推荐专家。请基于输入信息，评估作品在当前时间点（2026年2月）的推荐价值。
                请只返回 JSON，不要返回额外文本，格式如下：
                {"score": 85.5, "reason": "推荐理由，不超过50字"}
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

        for (int attempt = 0; attempt < STRUCTURED_RETRY_TIMES; attempt++) {
            String response = chat(systemPrompt, userMessage);
            if (response == null) {
                continue;
            }

            ScoreResult scoreResult = parseStructured(response, ScoreResult.class);
            if (scoreResult != null) {
                return toScoreMap(scoreResult);
            }
            log.warn("Analyze film parse failed, retry attempt={}", attempt + 1);
        }

        return createDefaultResult();
    }

    private Map<String, Object> toScoreMap(ScoreResult scoreResult) {
        Map<String, Object> result = new HashMap<>();
        double score = scoreResult.score == null ? 50.0 : scoreResult.score;
        score = Math.max(0.0, Math.min(score, 100.0));
        result.put("score", score);
        result.put("reason", scoreResult.reason == null || scoreResult.reason.isBlank()
                ? "AI分析完成"
                : scoreResult.reason);
        return result;
    }

    private Map<String, Object> createDefaultResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("score", 50.0);
        result.put("reason", "默认推荐");
        return result;
    }

    /**
     * Batch analyze and sort by AI score.
     */
    public List<Map<String, Object>> batchAnalyzeAndSort(List<Map<String, Object>> films) {
        log.info("Start AI batch analysis, film count={}", films.size());
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
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                log.error("Analyze film failed: title={}, error={}", film.get("title"), e.getMessage());
                film.put("aiScore", 50.0);
                film.put("aiReason", "分析失败");
            }
        }

        for (int i = maxAnalyze; i < films.size(); i++) {
            films.get(i).put("aiScore", 50.0);
            films.get(i).put("aiReason", "待分析");
        }

        films.sort((a, b) -> {
            Double scoreA = asDouble(a.get("aiScore"), 50.0);
            Double scoreB = asDouble(b.get("aiScore"), 50.0);
            return scoreB.compareTo(scoreA);
        });

        log.info("AI batch analysis done");
        return films;
    }

    /**
     * Intelligent filter for homepage content.
     */
    public List<Map<String, Object>> intelligentFilter(List<Map<String, Object>> films, int targetCount) {
        String systemPrompt = """
                你是首页内容策展专家。请从候选电影中选择最适合首页展示的条目。
                规则：兼顾时效性、题材多样性、热度；避免全是同类影片。
                只返回 JSON，不要返回额外文本：
                {"selected": [0, 3, 5, 7]}
                """;

        StringBuilder filmList = new StringBuilder();
        int listSize = Math.min(films.size(), 30);
        for (int i = 0; i < listSize; i++) {
            Map<String, Object> film = films.get(i);
            filmList.append(i)
                    .append(". ")
                    .append(film.get("title"))
                    .append(" (")
                    .append(film.get("year"))
                    .append(")\n");
        }

        String userMessage = String.format(
                "从以下 %d 部电影中选择 %d 部：\n%s",
                listSize,
                targetCount,
                filmList
        );

        for (int attempt = 0; attempt < STRUCTURED_RETRY_TIMES; attempt++) {
            String response = chat(systemPrompt, userMessage);
            if (response == null) {
                continue;
            }

            SelectedIndexes selected = parseStructured(response, SelectedIndexes.class);
            if (selected == null || selected.selected == null || selected.selected.isEmpty()) {
                continue;
            }

            Set<Integer> uniqueIndexes = new LinkedHashSet<>(selected.selected);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Integer idx : uniqueIndexes) {
                if (idx != null && idx >= 0 && idx < films.size()) {
                    result.add(films.get(idx));
                    if (result.size() >= targetCount) {
                        break;
                    }
                }
            }

            if (!result.isEmpty()) {
                return result;
            }
        }

        return films.subList(0, Math.min(targetCount, films.size()));
    }

    private Double asDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    private <T> T parseStructured(String raw, Class<T> clazz) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        // Try direct parse first.
        try {
            return objectMapper.readValue(raw, clazz);
        } catch (Exception ignored) {
            // fallback below
        }

        String jsonObject = extractFirstJsonObject(raw);
        if (jsonObject == null) {
            return null;
        }

        try {
            return objectMapper.readValue(jsonObject, clazz);
        } catch (Exception e) {
            log.warn("Structured parse failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractFirstJsonObject(String text) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        return null;
    }

    private static class ScoreResult {
        public Double score;
        public String reason;
    }

    private static class SelectedIndexes {
        public List<Integer> selected;
    }
}
