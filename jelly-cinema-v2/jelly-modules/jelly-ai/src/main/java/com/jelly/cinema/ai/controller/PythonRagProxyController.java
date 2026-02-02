package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.tools.PythonRagClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Python RAG 服务代理（避免浏览器直接跨域访问）
 */
@Slf4j
@RestController
@RequestMapping("/ai/rag/python")
@RequiredArgsConstructor
public class PythonRagProxyController {

    private final PythonRagClient pythonRagClient;

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", "")).trim();
        int topK = parseInt(body.getOrDefault("top_k", 5), 5);
        if (query.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }
        Map<String, Object> result = pythonRagClient.searchRaw(query, topK);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "offline", "message", "Python RAG service unavailable"));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        Map<String, Object> result = pythonRagClient.syncRaw();
        if (result == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("success", false, "message", "Python RAG service unavailable"));
        }
        return ResponseEntity.ok(result);
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

    private int parseInt(Object value, int fallback) {
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.debug("Failed to parse int from value: {}", value);
            return fallback;
        }
    }
}
