package com.jelly.cinema.film.service;

import com.jelly.cinema.film.domain.entity.Film;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 `t_film` 中的内容同步到 Python RAG。
 *
 * 这里复用 Python 服务的 ingest 接口，保证向量库与 PG chunk 元数据都由
 * 同一条链路生成，避免 Java 端与 Python 端写入格式不一致。
 */
@Slf4j
@Service
public class FilmRagSyncService {

    private final RestTemplate restTemplate;

    @Value("${ai.rag.python-service-url:http://localhost:8500}")
    private String pythonRagBaseUrl;

    public FilmRagSyncService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean syncFilm(Film film) {
        if (film == null || film.getId() == null) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("title", film.getTitle());
            body.put("content", buildFilmKnowledge(film));
            body.put("biz_type", "movie");
            body.put("source_type", "mysql_film");
            body.put("source_path", "mysql://t_film/" + film.getId());
            body.put("replace_by_source", true);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    pythonRagBaseUrl + "/rag/ingest",
                    entity,
                    Map.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && Boolean.TRUE.equals(response.getBody().get("success"));
            if (!success) {
                log.warn("电影同步到 Python RAG 失败: filmId={}, title={}", film.getId(), film.getTitle());
            }
            return success;
        } catch (Exception e) {
            log.warn("电影同步到 Python RAG 异常: filmId={}, title={}, err={}",
                    film.getId(), film.getTitle(), e.getMessage());
            return false;
        }
    }

    public int syncFilms(List<Film> films) {
        int count = 0;
        if (films == null || films.isEmpty()) {
            return count;
        }
        for (Film film : films) {
            if (syncFilm(film)) {
                count++;
            }
        }
        return count;
    }

    private String buildFilmKnowledge(Film film) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "片名", film.getTitle());
        appendLine(sb, "年份", film.getYear());
        appendLine(sb, "地区", film.getRegion());
        appendLine(sb, "导演", film.getDirector());
        appendLine(sb, "主演", film.getActors());
        appendLine(sb, "评分", film.getRating());
        appendLine(sb, "播放量", film.getPlayCount());
        appendLine(sb, "标签", film.getTags());
        appendLine(sb, "剧情简介", film.getDescription());
        sb.append("数据来源：MySQL t_film，已作为本地影视知识库内容同步到 RAG。");
        return sb.toString();
    }

    private void appendLine(StringBuilder sb, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return;
        }
        sb.append(label).append("：").append(text).append("\n");
    }
}
