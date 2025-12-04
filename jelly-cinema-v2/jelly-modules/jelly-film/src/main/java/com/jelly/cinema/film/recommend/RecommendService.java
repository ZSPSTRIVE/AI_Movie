package com.jelly.cinema.film.recommend;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.domain.entity.UserFavorite;
import com.jelly.cinema.film.domain.entity.WatchHistory;
import com.jelly.cinema.film.domain.vo.FilmVO;
import com.jelly.cinema.film.handler.SentinelFallbackHandler;
import com.jelly.cinema.film.mapper.FilmMapper;
import com.jelly.cinema.film.mapper.UserFavoriteMapper;
import com.jelly.cinema.film.mapper.WatchHistoryMapper;
import com.jelly.cinema.film.search.FilmSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 电影推荐服务
 * 
 * 推荐策略：
 * 1. 基于标签的推荐（Jaccard / 余弦相似度）
 * 2. 协同过滤推荐（User-CF）
 * 3. 热度推荐（评分 + 播放量 + 时效性）
 * 4. 混合推荐（多策略融合）
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final FilmMapper filmMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final FilmSearchService filmSearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis Key 前缀
     */
    private static final String USER_TAGS_KEY = "recommend:user:tags:";
    private static final String USER_HISTORY_KEY = "recommend:user:history:";
    private static final String SIMILAR_USERS_KEY = "recommend:similar:users:";
    private static final String RECOMMEND_RESULT_KEY = "recommend:result:";

    /**
     * 缓存过期时间
     */
    private static final long CACHE_EXPIRE_HOURS = 2;

    // ==================== 基于标签的推荐 ====================

    /**
     * 基于用户标签偏好推荐
     * 
     * 算法：
     * 1. 从用户的观看历史和收藏中提取标签偏好
     * 2. 计算电影与用户偏好的相似度（Jaccard 相似度）
     * 3. 按相似度排序返回
     */
    @SentinelResource(value = "recommendFilm",
            blockHandler = "recommendFilmFallback",
            blockHandlerClass = SentinelFallbackHandler.class)
    public List<Long> recommendByTags(Long userId, int size) {
        log.info("基于标签推荐: userId={}, size={}", userId, size);

        // 1. 获取用户标签偏好
        Map<String, Double> userTags = getUserTagPreferences(userId);
        if (userTags.isEmpty()) {
            log.debug("用户无标签偏好，返回热门推荐: userId={}", userId);
            return getHotRecommend(size);
        }

        // 2. 获取用户已看过的电影
        Set<Long> watchedIds = getWatchedFilmIds(userId);

        // 3. 获取候选电影（按评分排序）
        List<Film> candidates = filmMapper.selectList(null);

        // 4. 计算相似度并排序
        List<FilmScore> scores = new ArrayList<>();
        for (Film film : candidates) {
            // 排除已看过的
            if (watchedIds.contains(film.getId())) {
                continue;
            }

            // 计算 Jaccard 相似度
            double similarity = calculateJaccardSimilarity(userTags, parseFilmTags(film.getTags()));
            if (similarity > 0) {
                scores.add(new FilmScore(film.getId(), similarity));
            }
        }

        // 排序并返回 Top N
        return scores.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(size)
                .map(s -> s.filmId)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户标签偏好
     */
    @SuppressWarnings("unchecked")
    private Map<String, Double> getUserTagPreferences(Long userId) {
        String cacheKey = USER_TAGS_KEY + userId;

        // 尝试从缓存获取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Map<String, Double>) cached;
        }

        // 从收藏和观看历史中提取标签
        Map<String, Double> tagWeights = new HashMap<>();

        // 收藏的电影（权重高）
        List<UserFavorite> favorites = userFavoriteMapper.selectByUserId(userId);
        for (UserFavorite favorite : favorites) {
            Film film = filmMapper.selectById(favorite.getFilmId());
            if (film != null && film.getTags() != null) {
                for (String tag : film.getTags().split(",")) {
                    tag = tag.trim();
                    tagWeights.merge(tag, 2.0, Double::sum);
                }
            }
        }

        // 观看历史（权重低）
        List<WatchHistory> histories = watchHistoryMapper.selectByUserId(userId);
        for (WatchHistory history : histories) {
            Film film = filmMapper.selectById(history.getFilmId());
            if (film != null && film.getTags() != null) {
                for (String tag : film.getTags().split(",")) {
                    tag = tag.trim();
                    tagWeights.merge(tag, 1.0, Double::sum);
                }
            }
        }

        // 归一化
        if (!tagWeights.isEmpty()) {
            double maxWeight = tagWeights.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            tagWeights.replaceAll((k, v) -> v / maxWeight);
        }

        // 缓存
        if (!tagWeights.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, tagWeights, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        return tagWeights;
    }

    /**
     * 解析电影标签
     */
    private Set<String> parseFilmTags(String tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 计算 Jaccard 相似度
     */
    private double calculateJaccardSimilarity(Map<String, Double> userTags, Set<String> filmTags) {
        if (userTags.isEmpty() || filmTags.isEmpty()) {
            return 0.0;
        }

        Set<String> userTagSet = userTags.keySet();

        // 交集
        Set<String> intersection = new HashSet<>(userTagSet);
        intersection.retainAll(filmTags);

        // 并集
        Set<String> union = new HashSet<>(userTagSet);
        union.addAll(filmTags);

        // 带权重的相似度
        double weightedScore = 0.0;
        for (String tag : intersection) {
            weightedScore += userTags.getOrDefault(tag, 0.0);
        }

        return weightedScore / union.size();
    }

    // ==================== 协同过滤推荐（User-CF）====================

    /**
     * 基于用户协同过滤推荐
     * 
     * 算法：
     * 1. 找到与当前用户相似的用户（基于观看历史）
     * 2. 推荐相似用户喜欢但当前用户未看过的电影
     */
    public List<Long> recommendByUserCF(Long userId, int size) {
        log.info("协同过滤推荐: userId={}, size={}", userId, size);

        // 1. 获取当前用户的观看历史
        Set<Long> userFilms = getWatchedFilmIds(userId);
        if (userFilms.isEmpty()) {
            return getHotRecommend(size);
        }

        // 2. 找相似用户
        List<Long> similarUsers = findSimilarUsers(userId, userFilms, 10);
        if (similarUsers.isEmpty()) {
            return recommendByTags(userId, size);
        }

        // 3. 收集相似用户喜欢的电影
        Map<Long, Integer> filmScores = new HashMap<>();
        for (Long similarUserId : similarUsers) {
            Set<Long> similarUserFilms = getWatchedFilmIds(similarUserId);
            for (Long filmId : similarUserFilms) {
                if (!userFilms.contains(filmId)) {
                    filmScores.merge(filmId, 1, Integer::sum);
                }
            }
        }

        // 4. 按推荐次数排序
        return filmScores.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(size)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 找相似用户
     */
    private List<Long> findSimilarUsers(Long userId, Set<Long> userFilms, int topN) {
        // 获取所有用户的观看历史
        List<WatchHistory> allHistories = watchHistoryMapper.selectList(null);

        // 按用户分组
        Map<Long, Set<Long>> userFilmsMap = allHistories.stream()
                .filter(h -> !h.getUserId().equals(userId))
                .collect(Collectors.groupingBy(
                        WatchHistory::getUserId,
                        Collectors.mapping(WatchHistory::getFilmId, Collectors.toSet())
                ));

        // 计算相似度
        List<UserSimilarity> similarities = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : userFilmsMap.entrySet()) {
            Set<Long> otherUserFilms = entry.getValue();

            // Jaccard 相似度
            Set<Long> intersection = new HashSet<>(userFilms);
            intersection.retainAll(otherUserFilms);

            Set<Long> union = new HashSet<>(userFilms);
            union.addAll(otherUserFilms);

            if (!union.isEmpty()) {
                double similarity = (double) intersection.size() / union.size();
                if (similarity > 0.1) { // 相似度阈值
                    similarities.add(new UserSimilarity(entry.getKey(), similarity));
                }
            }
        }

        // 返回 Top N 相似用户
        return similarities.stream()
                .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                .limit(topN)
                .map(s -> s.userId)
                .collect(Collectors.toList());
    }

    // ==================== 热门推荐 ====================

    /**
     * 热门推荐（评分 + 播放量 + 时效性）
     */
    public List<Long> getHotRecommend(int size) {
        log.info("热门推荐: size={}", size);

        // 综合评分公式：score = rating * 0.4 + log(playCount) * 0.3 + timeDecay * 0.3
        // 这里简化为按评分和播放量排序
        List<Film> films = filmMapper.selectList(null);

        return films.stream()
                .sorted((a, b) -> {
                    // 综合得分
                    double scoreA = calculateHotScore(a);
                    double scoreB = calculateHotScore(b);
                    return Double.compare(scoreB, scoreA);
                })
                .limit(size)
                .map(Film::getId)
                .collect(Collectors.toList());
    }

    /**
     * 计算热度得分
     */
    private double calculateHotScore(Film film) {
        double rating = film.getRating() != null ? film.getRating() : 0;
        long playCount = film.getPlayCount() != null ? film.getPlayCount() : 0;

        // 评分权重 0.4 + 播放量对数权重 0.6
        return rating * 0.4 + Math.log10(playCount + 1) * 0.6;
    }

    // ==================== 混合推荐 ====================

    /**
     * 混合推荐（多策略融合）
     * 
     * 融合策略：
     * - 标签推荐 40%
     * - 协同过滤 30%
     * - 热门推荐 20%
     * - 新片推荐 10%
     */
    public List<Long> hybridRecommend(Long userId, int size) {
        log.info("混合推荐: userId={}, size={}", userId, size);

        String cacheKey = RECOMMEND_RESULT_KEY + userId;

        // 尝试从缓存获取
        @SuppressWarnings("unchecked")
        List<Long> cached = (List<Long>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        Set<Long> resultSet = new LinkedHashSet<>();

        // 1. 标签推荐（40%）
        int tagSize = (int) (size * 0.4);
        List<Long> tagRecommends = recommendByTags(userId, tagSize);
        resultSet.addAll(tagRecommends);

        // 2. 协同过滤推荐（30%）
        int cfSize = (int) (size * 0.3);
        List<Long> cfRecommends = recommendByUserCF(userId, cfSize);
        resultSet.addAll(cfRecommends);

        // 3. 热门推荐（30%）
        int hotSize = size - resultSet.size();
        if (hotSize > 0) {
            List<Long> hotRecommends = getHotRecommend(hotSize * 2);
            for (Long id : hotRecommends) {
                if (resultSet.size() >= size) break;
                resultSet.add(id);
            }
        }

        List<Long> result = new ArrayList<>(resultSet);

        // 缓存结果
        if (!result.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        return result;
    }

    /**
     * 相似电影推荐
     */
    public List<Long> getSimilarFilms(Long filmId, int size) {
        Film film = filmMapper.selectById(filmId);
        if (film == null) {
            return Collections.emptyList();
        }

        return filmSearchService.findSimilar(filmId, film.getTags(), film.getDescription(), size);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取用户已看过的电影 ID
     */
    private Set<Long> getWatchedFilmIds(Long userId) {
        String cacheKey = USER_HISTORY_KEY + userId;

        @SuppressWarnings("unchecked")
        Set<Long> cached = (Set<Long>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<WatchHistory> histories = watchHistoryMapper.selectByUserId(userId);
        Set<Long> filmIds = histories.stream()
                .map(WatchHistory::getFilmId)
                .collect(Collectors.toSet());

        if (!filmIds.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, filmIds, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        return filmIds;
    }

    /**
     * 清除用户推荐缓存
     */
    public void clearUserRecommendCache(Long userId) {
        redisTemplate.delete(USER_TAGS_KEY + userId);
        redisTemplate.delete(USER_HISTORY_KEY + userId);
        redisTemplate.delete(RECOMMEND_RESULT_KEY + userId);
        log.info("清除用户推荐缓存: userId={}", userId);
    }

    // ==================== 内部类 ====================

    private static class FilmScore {
        Long filmId;
        double score;

        FilmScore(Long filmId, double score) {
            this.filmId = filmId;
            this.score = score;
        }
    }

    private static class UserSimilarity {
        Long userId;
        double similarity;

        UserSimilarity(Long userId, double similarity) {
            this.userId = userId;
            this.similarity = similarity;
        }
    }
}
