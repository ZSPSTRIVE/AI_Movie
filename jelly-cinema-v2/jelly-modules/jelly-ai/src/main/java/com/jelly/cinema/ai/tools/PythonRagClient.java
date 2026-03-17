package com.jelly.cinema.ai.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

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

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public PythonRagClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Java 保留业务编排和对外接口，Python 专注 RAG 检索链路，两边通过 HTTP 解耦。
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30));
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 调用 Python RAG 服务进行向量检索
     *
     * @param query 查询内容
     * @param topK  返回结果数量
     * @return 检索结果的可读文本
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
                    .block(REQUEST_TIMEOUT);

            if (responseJson == null || responseJson.isBlank()) {
                return "知识库中未找到足够依据。";
            }

            JsonNode root = objectMapper.readTree(responseJson);
            StringBuilder sb = new StringBuilder();

            String answer = text(root.get("answer"));
            if (!answer.isBlank()) {
                sb.append(answer.trim());
            }

            JsonNode references = root.get("references");
            if (references != null && references.isArray() && !references.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("【参考来源】\n");
                for (int i = 0; i < references.size(); i++) {
                    JsonNode item = references.get(i);
                    sb.append(i + 1).append(". ");
                    sb.append(text(item.get("title"), "未知文档"));
                    sb.append(" (chunk=").append(item.path("chunk_id").asText("?")).append(")");
                    sb.append(" (score=").append(String.format("%.2f", item.path("score").asDouble(0))).append(")\n");
                }
                return sb.toString().trim();
            }

            JsonNode results = root.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return sb.length() > 0 ? sb.toString() : "知识库中未找到足够依据。";
            }

            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("【知识库检索结果】\n\n");

            for (int i = 0; i < results.size(); i++) {
                JsonNode item = results.get(i);
                String title = text(item.get("title"), "未知");
                String filmId = text(item.get("film_id"));
                String content = text(item.get("content"));
                double score = item.path("score").asDouble(0);

                sb.append(i + 1).append(". ");
                if (!filmId.isBlank()) {
                    sb.append("[").append(title).append("](/film/").append(filmId).append(")");
                } else {
                    sb.append("《").append(title).append("》");
                }
                sb.append(" (相关度: ").append(String.format("%.2f", score)).append(")\n");

                String summary = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                sb.append("   ").append(summary).append("\n\n");
            }

            return sb.toString().trim();

        } catch (Exception e) {
            log.error("调用 Python RAG 服务失败", e);
            return "知识库服务暂时不可用。";
        }
    }

    /**
     * 向 Python RAG 写入文档
     *
     * @return 文档 ID，失败时返回 null
     */
    public Long ingest(String title, String content, String bizType, String sourceType, String sourcePath) {
        try {
            String url = pythonServiceUrl + "/rag/ingest";
            Map<String, Object> requestBody = Map.of(
                    "title", title,
                    "content", content,
                    "biz_type", bizType,
                    "source_type", sourceType,
                    "source_path", sourcePath == null ? "" : sourcePath
            );
            String responseJson = webClient.post()
                    .uri(url)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);
            if (responseJson == null || responseJson.isBlank()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(responseJson);
            if (!root.path("success").asBoolean(false)) {
                return null;
            }
            return root.path("document_id").asLong();
        } catch (Exception e) {
            log.error("Python RAG ingest failed", e);
            return null;
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
                    .block(REQUEST_TIMEOUT);
            return parseJson(responseJson);
        } catch (Exception e) {
            log.error("Python RAG searchRaw failed", e);
            return null;
        }
    }

    /**
     * 兼容旧入口：触发目录重建
     */
    public String syncFilms() {
        try {
            String url = pythonServiceUrl + "/rag/sync";

            String responseJson = webClient.post()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);

            if (responseJson == null || responseJson.isBlank()) {
                return "重建请求无响应";
            }

            JsonNode root = objectMapper.readTree(responseJson);
            boolean success = root.path("success").asBoolean(false);
            int count = root.path("count").asInt(0);
            String message = text(root.get("message"));

            if (success) {
                return "重建成功，共处理 " + count + " 个知识文件。";
            }
            return "重建失败: " + message;

        } catch (Exception e) {
            log.error("重建 Python RAG 索引失败", e);
            return "Python RAG 服务暂时不可用: " + e.getMessage();
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
                    .block(REQUEST_TIMEOUT);
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
                    .block(REQUEST_TIMEOUT);
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
                    .block(REQUEST_TIMEOUT);

            return response != null && response.contains("\"status\"");
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

    private String text(JsonNode node) {
        return text(node, "");
    }

    private String text(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null ? fallback : value;
    }
}
