package com.jelly.cinema.common.api.feign;

import com.jelly.cinema.common.api.domain.RemoteFilm;
import com.jelly.cinema.common.api.feign.fallback.RemoteFilmFallbackFactory;
import com.jelly.cinema.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 电影服务 Feign 接口
 * 用于 AI 服务调用电影服务的能力
 *
 * @author Jelly Cinema
 * @since 2026
 */
@FeignClient(value = "jelly-film", fallbackFactory = RemoteFilmFallbackFactory.class)
public interface RemoteFilmService {

    /**
     * 根据 ID 获取电影详情
     *
     * @param id 电影ID
     * @return 电影详情
     */
    @GetMapping("/film/detail/{id}")
    R<RemoteFilm> getFilmById(@PathVariable("id") Long id);

    /**
     * 搜索电影
     *
     * @param keyword 搜索关键词
     * @return 电影列表
     */
    @GetMapping("/film/search")
    R<List<RemoteFilm>> searchFilms(@RequestParam("keyword") String keyword);

    /**
     * 获取推荐电影
     *
     * @param size 数量
     * @return 推荐电影列表
     */
    @GetMapping("/film/recommend/feed")
    R<List<RemoteFilm>> getRecommendFilms(@RequestParam(value = "size", defaultValue = "10") Integer size);

    /**
     * 获取热门榜单
     *
     * @param size 数量
     * @return 热门电影列表
     */
    @GetMapping("/film/recommend/hot")
    R<List<RemoteFilm>> getHotFilms(@RequestParam(value = "size", defaultValue = "10") Integer size);

    /**
     * 将电影库同步到 Python RAG
     *
     * @param limit 同步数量
     * @return 实际同步成功数量
     */
    @PostMapping("/film/rag/sync")
    R<Integer> syncFilmsToRag(@RequestParam(value = "limit", required = false) Integer limit);

    /**
     * 后台触发电影库同步到 Python RAG
     *
     * @param limit 同步数量
     * @return 当前任务状态
     */
    @PostMapping("/film/rag/sync/start")
    R<Map<String, Object>> startFilmsToRagSync(@RequestParam(value = "limit", required = false) Integer limit);

    /**
     * 查询电影库同步到 Python RAG 的任务状态
     *
     * @return 当前任务状态
     */
    @GetMapping("/film/rag/sync/status")
    R<Map<String, Object>> getFilmsToRagSyncStatus();
}
