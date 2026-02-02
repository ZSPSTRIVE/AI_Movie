package com.jelly.cinema.ai.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Python RAG 服务客户端
 * 用于调用 jelly-rag-python 服务的 HTTP 接口
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Component
public class PythonRagClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.rag.python-service-url:http://localhost:8500}")
    private String pythonServiceUrl;

    public PythonRagClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 调用 Python RAG 服务进行向量检索
     *
     * @param query 查询内容
     * @param topK  返回结果数量
     * @return 检索到的内容拼接字符串
     */
    public String search(String query, int topK) {
        try {
            String url = pythonServiceUrl + "/rag/search";
            
            Map<String, Object> requestBody = Map.of(
                    "query", query,
                    "top_k", topK
            );

            String responseJson = webClient.post()
                    .uri(url)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null) {
                return "";
            }

            // 解析响应
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode results = root.get("results");
            
            if (results == null || !results.isArray() || results.isEmpty()) {
                return "未找到相关信息。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【知识库检索结果】\n\n");
            
            for (int i = 0; i < results.size(); i++) {
                JsonNode item = results.get(i);
                String title = item.has("title") ? item.get("title").asText() : "未知";
                String filmId = item.has("film_id") ? item.get("film_id").asText() : null;
                String content = item.has("content") ? item.get("content").asText() : "";
                double score = item.has("score") ? item.get("score").asDouble() : 0;
                
                sb.append(i + 1).append(". ");
                if (filmId != null && !filmId.isBlank()) {
                    sb.append("[").append(title).append("](/film/").append(filmId).append(")");
                } else {
                    sb.append("《").append(title).append("》");
                }
                sb.append(" (相关度: ").append(String.format("%.2f", score)).append(")\n");
                
                // 截取内容摘要
                String summary = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                sb.append("   ").append(summary).append("\n\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("调用 Python RAG 服务失败", e);
            return "知识库服务暂时不可用。";
        }
    }

    /**
     * 获取原始 JSON 结果（用于前端代理）
     */
    public Map<String, Object> searchRaw(String query, int topK) {
        try {
            String url = pythonServiceUrl + "/rag/search";
            Map<String, Object> requestBody = Map.of(
                    "query", query,
                    "top_k", topK
            );
            String responseJson = webClient.post()
                    .uri(url)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseJson(responseJson);
        } catch (Exception e) {
            log.error("Python RAG searchRaw failed", e);
            return null;
        }
    }

    /**
     * 触发电影数据同步到 Milvus
     *
     * @return 同步结果
     */
    public String syncFilms() {
        try {
            String url = pythonServiceUrl + "/rag/sync";
            
            String responseJson = webClient.post()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseJson == null) {
                return "同步请求无响应";
            }

            JsonNode root = objectMapper.readTree(responseJson);
            boolean success = root.has("success") && root.get("success").asBoolean();
            int count = root.has("count") ? root.get("count").asInt() : 0;
            String message = root.has("message") ? root.get("message").asText() : "";
            
            if (success) {
                return "同步成功，共同步 " + count + " 部电影到向量库。";
            } else {
                return "同步失败: " + message;
            }
            
        } catch (Exception e) {
            log.error("同步电影数据失败", e);
            return "同步服务暂时不可用: " + e.getMessage();
        }
    }

    /**
     * 同步原始 JSON 结果（用于前端代理）
     */
    public Map<String, Object> syncRaw() {
        try {
            String url = pythonServiceUrl + "/rag/sync";
            String responseJson = webClient.post()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseJson(responseJson);
        } catch (Exception e) {
            log.error("Python RAG syncRaw failed", e);
            return null;
        }
    }

    /**
     * 健康检查原始 JSON（用于前端代理）
     */
    public Map<String, Object> healthRaw() {
        try {
            String url = pythonServiceUrl + "/health";
            String responseJson = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseJson(responseJson);
        } catch (Exception e) {
            log.warn("Python RAG healthRaw failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 健康检查
     *
     * @return 服务是否可用
     */
    public boolean isHealthy() {
        try {
            String url = pythonServiceUrl + "/health";
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            return response != null && response.contains("healthy");
        } catch (Exception e) {
            log.warn("Python RAG 服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON from Python RAG: {}", e.getMessage());
            return null;
        }
    }
}
