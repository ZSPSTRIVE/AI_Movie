package com.jelly.cinema.ai.tools;

import com.jelly.cinema.common.api.domain.RemoteFilm;
import com.jelly.cinema.common.api.feign.RemoteFilmService;
import com.jelly.cinema.common.core.domain.R;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 电影工具集
 * 基于 LangChain4j @Tool 注解实现，供 AI Agent 自动调用
 * 
 * 包含：
 * - 电影搜索
 * - 电影详情获取
 * - 推荐获取
 * - 热门榜单获取
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiMovieTools {

    private final RemoteFilmService remoteFilmService;
    private final PythonRagClient pythonRagClient;

    /**
     * 搜索电影
     * 当用户想要查找电影时调用此工具
     *
     * @param keyword 搜索关键词，如电影名、演员名、导演名
     * @return 搜索结果的 JSON 格式字符串
     */
    @Tool("搜索电影。当用户询问某部电影、想看某个电影、查找电影时使用。参数 keyword 是搜索关键词。")
    public String searchMovies(String keyword) {
        log.info("🔧 Tool: searchMovies('{}')", keyword);
        try {
            R<List<RemoteFilm>> result = remoteFilmService.searchFilms(keyword);
            if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
                List<RemoteFilm> films = result.getData();
                return formatFilmList(films, "搜索结果");
            }
            return "没有找到与 '" + keyword + "' 相关的电影。";
        } catch (Exception e) {
            log.error("搜索电影失败", e);
            return "搜索电影时出现问题，请稍后重试。";
        }
    }

    /**
     * 获取电影详情
     * 当用户想了解某部具体电影的详细信息时调用
     *
     * @param filmId 电影ID
     * @return 电影详情的 JSON 格式字符串
     */
    @Tool("获取电影详情。当需要了解某部电影的详细信息（如演员、导演、剧情）时使用。参数 filmId 是电影的ID。")
    public String getMovieDetail(Long filmId) {
        log.info("🔧 Tool: getMovieDetail({})", filmId);
        try {
            R<RemoteFilm> result = remoteFilmService.getFilmById(filmId);
            if (result.isSuccess() && result.getData() != null) {
                RemoteFilm film = result.getData();
                return formatFilmDetail(film);
            }
            return "未找到ID为 " + filmId + " 的电影。";
        } catch (Exception e) {
            log.error("获取电影详情失败", e);
            return "获取电影详情时出现问题，请稍后重试。";
        }
    }

    /**
     * 获取推荐电影
     * 当用户想要看推荐、不知道看什么时调用
     *
     * @param count 推荐数量，默认5部
     * @return 推荐电影列表
     */
    @Tool("获取推荐电影。当用户问'有什么好看的电影'、'推荐几部电影'时使用。参数 count 是推荐数量。")
    public String getRecommendedMovies(int count) {
        log.info("🔧 Tool: getRecommendedMovies({})", count);
        try {
            R<List<RemoteFilm>> result = remoteFilmService.getRecommendFilms(count > 0 ? count : 5);
            if (result.isSuccess() && result.getData() != null) {
                return formatFilmList(result.getData(), "为你推荐");
            }
            return "暂时没有推荐内容。";
        } catch (Exception e) {
            log.error("获取推荐电影失败", e);
            return "获取推荐时出现问题，请稍后重试。";
        }
    }

    /**
     * 获取热门榜单
     * 当用户想看热门、排行榜时调用
     *
     * @param count 榜单数量，默认10部
     * @return 热门电影列表
     */
    @Tool("获取热门电影榜单。当用户问'最近什么电影火'、'热门排行'时使用。参数 count 是榜单数量。")
    public String getHotMovies(int count) {
        log.info("🔧 Tool: getHotMovies({})", count);
        try {
            R<List<RemoteFilm>> result = remoteFilmService.getHotFilms(count > 0 ? count : 10);
            if (result.isSuccess() && result.getData() != null) {
                return formatFilmList(result.getData(), "热门榜单");
            }
            return "暂时没有热门榜单数据。";
        } catch (Exception e) {
            log.error("获取热门榜单失败", e);
            return "获取热门榜单时出现问题，请稍后重试。";
        }
    }

    /**
     * RAG 知识库检索
     * 当用户询问需要从知识库检索的问题时调用
     *
     * @param query 查询内容
     * @return 检索到的相关内容
     */
    @Tool("从知识库检索信息。当用户问的问题可能需要查阅文档、背景资料时使用。参数 query 是查询内容。")
    public String ragSearch(String query) {
        log.info("🔧 Tool: ragSearch('{}')", query);
        try {
            if (query != null && !query.isBlank()) {
                remoteFilmService.searchFilms(query.trim());
            }
            return pythonRagClient.search(query, 3);
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return "知识库检索时出现问题。";
        }
    }

    // ==================== 格式化方法 ====================

    private String formatFilmList(List<RemoteFilm> films, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(title).append("\n\n");
        
        for (int i = 0; i < films.size(); i++) {
            RemoteFilm film = films.get(i);
            // 使用 Markdown 链接格式 [电影名](/film/ID)
            sb.append(i + 1).append(". **[").append(film.getTitle()).append("](/film/").append(film.getId()).append(")**");
            
            if (film.getYear() != null) {
                sb.append(" (").append(film.getYear()).append(")");
            }
            if (film.getRating() != null) {
                sb.append(" ⭐").append(film.getRating());
            }
            sb.append("\n");
            
            // 简要信息行
            sb.append("   > ");
            if (film.getCategoryName() != null) {
                sb.append(film.getCategoryName());
            }
            if (film.getRegion() != null) {
                sb.append(" · ").append(film.getRegion());
            }
            sb.append("\n\n");
        }
        
        return sb.toString();
    }

    private String formatFilmDetail(RemoteFilm film) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 📽️ [").append(film.getTitle()).append("](/film/").append(film.getId()).append(")\n\n");
        
        if (film.getYear() != null) sb.append("**年份**: ").append(film.getYear()).append("  ");
        if (film.getRating() != null) sb.append("**评分**: ⭐").append(film.getRating()).append("\n");
        if (film.getCategoryName() != null) sb.append("**类型**: ").append(film.getCategoryName()).append("  ");
        if (film.getRegion() != null) sb.append("**地区**: ").append(film.getRegion()).append("\n");
        
        sb.append("\n");
        
        if (film.getDirector() != null) {
            sb.append("**导演**: ").append(film.getDirector()).append("\n");
        }
        if (film.getActors() != null) {
            sb.append("**主演**: ").append(film.getActors()).append("\n");
        }
        
        if (film.getDescription() != null) {
            sb.append("\n**📝 剧情简介**:\n").append(film.getDescription()).append("\n");
        }
        
        sb.append("\n[▶️ 立即播放](/film/").append(film.getId()).append(")");
        
        return sb.toString();
    }
}
