package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.tools.PythonRagClient;
import com.jelly.cinema.common.api.feign.RemoteFilmService;
import com.jelly.cinema.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Python RAG 服务代理（避免浏览器直接跨域访问）。
 *
 * 搜索前会先触发电影服务补库，确保检索链路遵循：
 * TVBox/API -> MySQL t_film -> Python RAG。
 */
@Slf4j
@RestController
@RequestMapping("/ai/rag/python")
@RequiredArgsConstructor
public class PythonRagProxyController {

    private final PythonRagClient pythonRagClient;
    private final RemoteFilmService remoteFilmService;

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", "")).trim();
        int topK = parseInt(body.getOrDefault("top_k", 5), 5);
        if (query.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }

        prewarmFilmLibrary(query);

        Map<String, Object> result = pythonRagClient.searchRaw(query, topK);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "offline", "message", "Python RAG service unavailable"));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@RequestBody(required = false) Map<String, Object> body) {
        Integer limit = body == null ? null : parseNullableInt(body.get("limit"));
        try {
            R<Integer> response = remoteFilmService.syncFilmsToRag(limit);
            if (response == null || !response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "success", false,
                                "message", response == null ? "Film service unavailable" : response.getMsg()
                        ));
            }
            int count = response.getData() == null ? 0 : response.getData();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", count,
                    "message", "已按 MySQL -> Python RAG 链路完成同步"
            ));
        } catch (Exception e) {
            log.error("Sync films to Python RAG through film service failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> result = pythonRagClient.healthRaw();
        if (result == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "offline"));
        }
        return ResponseEntity.ok(result);
    }

    private void prewarmFilmLibrary(String query) {
        try {
            remoteFilmService.searchFilms(query);
        } catch (Exception e) {
            log.debug("Prewarm film library failed: query={}, err={}", query, e.getMessage());
        }
    }

    private int parseInt(Object value, int fallback) {
        Integer parsed = parseNullableInt(value);
        return parsed == null ? fallback : parsed;
    }

    private Integer parseNullableInt(Object value) {
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Integer.parseInt(text);
        } catch (Exception e) {
            log.debug("Failed to parse int from value: {}", value);
            return null;
        }
    }
}
