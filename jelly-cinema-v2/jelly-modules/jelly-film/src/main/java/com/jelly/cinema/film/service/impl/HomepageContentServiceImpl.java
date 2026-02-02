package com.jelly.cinema.film.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.film.domain.entity.HomepageContent;
import com.jelly.cinema.film.domain.vo.HomepageContentVO;
import com.jelly.cinema.film.mapper.HomepageContentMapper;
import com.jelly.cinema.film.service.HomepageContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;
import com.jelly.cinema.film.domain.entity.PublishedContent;
import com.jelly.cinema.film.mapper.HomepageConfigVersionMapper;
import com.jelly.cinema.film.mapper.PublishedContentMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;
import com.jelly.cinema.film.domain.entity.PublishedContent;
import com.jelly.cinema.film.mapper.HomepageConfigVersionMapper;
import com.jelly.cinema.film.mapper.PublishedContentMapper;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页内容管理服务实现
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomepageContentServiceImpl extends ServiceImpl<HomepageContentMapper, HomepageContent>
        implements HomepageContentService {

    private final HomepageContentMapper homepageContentMapper;
    private final PublishedContentMapper publishedContentMapper;
    private final HomepageConfigVersionMapper configVersionMapper;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final com.jelly.cinema.film.service.FilmService filmService;

    private static final String CACHE_KEY_PREFIX = "homepage:content:";
    private static final long CACHE_TTL_MINUTES = 5;

    @Override
    public PageResult<HomepageContentVO> pageList(String contentType, String sectionType,
                                                   Integer pageNum, Integer pageSize) {
        Page<HomepageContent> page = new Page<>(pageNum, pageSize);
        
        LambdaQueryWrapper<HomepageContent> wrapper = new LambdaQueryWrapper<>();
        if (contentType != null && !contentType.isEmpty()) {
            wrapper.eq(HomepageContent::getContentType, contentType);
        }
        if (sectionType != null && !sectionType.isEmpty()) {
            wrapper.eq(HomepageContent::getSectionType, sectionType);
        }
        wrapper.eq(HomepageContent::getDeleted, 0);
        wrapper.orderByAsc(HomepageContent::getSortOrder);
        
        Page<HomepageContent> result = homepageContentMapper.selectPage(page, wrapper);
        
        List<HomepageContentVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
                
        return new PageResult<>(voList, result.getTotal());
    }

    @Override
    public List<HomepageContentVO> getRecommendList(Integer limit) {
        List<HomepageContentVO> published = getPublishedList("movie", "recommend", limit);
        if (!published.isEmpty()) {
            return published;
        }
        try {
            List<HomepageContent> list = homepageContentMapper.selectRecommendList(limit);
            return list.stream().map(this::convertToVO).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取推荐列表失败(表可能不存在): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<HomepageContentVO> getHotList(Integer limit) {
        List<HomepageContentVO> published = getPublishedList("movie", "hot", limit);
        if (!published.isEmpty()) {
            return published;
        }
        try {
            return getListBySectionType("hot", limit);
        } catch (Exception e) {
            log.warn("获取热门列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<HomepageContentVO> getMovieList(Integer limit) {
        List<HomepageContentVO> published = getPublishedList("movie", null, limit);
        if (!published.isEmpty()) {
            return published;
        }
        return getListByContentType("movie", limit);
    }

    @Override
    public List<HomepageContentVO> getTvSeriesList(Integer limit) {
        List<HomepageContentVO> published = getPublishedList("tv_series", null, limit);
        if (!published.isEmpty()) {
            return published;
        }
        return getListByContentType("tv_series", limit);
    }

    @Override
    public void refreshContent() {
        log.info("开始刷新首页内容...");
        
        try {
            // 调用 TVBox Proxy API 获取推荐列表
            String tvboxProxyUrl = "http://localhost:3001/api/tvbox/recommend?limit=50";
            ResponseEntity<Map> response = restTemplate.getForEntity(tvboxProxyUrl, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> films = (List<Map<String, Object>>) response.getBody().get("data");
                
                if (films != null && !films.isEmpty()) {
                    log.info("从TVBox获取到 {} 部电影", films.size());
                    
                    // 清除旧数据
                    LambdaQueryWrapper<HomepageContent> deleteWrapper = new LambdaQueryWrapper<>();
                    deleteWrapper.eq(HomepageContent::getSectionType, "recommend");
                    homepageContentMapper.delete(deleteWrapper);
                    
                    // 保存新数据
                    int sortOrder = 0;
                    for (Map<String, Object> film : films) {
                        HomepageContent content = new HomepageContent();
                        content.setTvboxId((String) film.get("id"));
                        content.setTitle((String) film.get("title"));
                        content.setCoverUrl((String) film.get("coverUrl"));
                        content.setDescription((String) film.get("description"));
                        content.setSourceName((String) film.get("sourceName"));
                        content.setRating(new BigDecimal(film.getOrDefault("rating", 0.0).toString()));
                        content.setYear((Integer) film.get("year"));
                        content.setRegion((String) film.get("region"));
                        content.setActors((String) film.get("actors"));
                        content.setDirector((String) film.get("director"));
                        content.setContentType("movie"); // 默认电影类型
                        content.setSectionType("recommend"); // 推荐板块
                        content.setSortOrder(sortOrder++);
                        content.setStatus(1); // 启用
                        
                        save(content);
                    }
                    
                    clearCache();
                    log.info("首页内容刷新完成，成功保存 {} 部电影", films.size());

                    // 异步同步到电影库 (t_film)
                    final List<Map<String, Object>> filmsToSync = new ArrayList<>(films);
                    new Thread(() -> syncToLibrary(filmsToSync)).start();

                } else {
                    log.warn("TVBox返回数据为空");
                }
            } else {
                log.error("TVBox Proxy请求失败");
            }
            
        } catch (Exception e) {
            log.error("刷新首页内容失败: {}", e.getMessage(), e);
            throw new RuntimeException("刷新失败: " + e.getMessage());
        }
    }

    @Override
    public void aiSort() {
        log.info("开始AI智能排序...");
        
        try {
            // 获取所有启用的推荐内容
            LambdaQueryWrapper<HomepageContent> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(HomepageContent::getSectionType, "recommend");
            wrapper.eq(HomepageContent::getStatus, 1);
            wrapper.eq(HomepageContent::getDeleted, 0);
            List<HomepageContent> contents = list(wrapper);
            
            if (contents.isEmpty()) {
                log.warn("没有需要排序的内容");
                return;
            }
            
            log.info("准备对 {} 部电影进行AI分析", contents.size());
            
            // 转换为Map列表供AI分析
            List<Map<String, Object>> filmMaps = new ArrayList<>();
            for (HomepageContent content : contents) {
                Map<String, Object> filmMap = new HashMap<>();
                filmMap.put("id", content.getId());
                filmMap.put("title", content.getTitle());
                filmMap.put("description", content.getDescription());
                filmMap.put("year", content.getYear());
                filmMap.put("actors", content.getActors());
                filmMap.put("director", content.getDirector());
                filmMap.put("region", content.getRegion());
                filmMaps.add(filmMap);
            }
            
            // 通过HTTP调用jelly-ai服务进行AI分析
            List<Map<String, Object>> analyzed = null;
            try {
                String aiServiceUrl = "http://localhost:9500/ai/analyze/batch-sort";
                ResponseEntity<Map> response = restTemplate.postForEntity(aiServiceUrl, filmMaps, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    analyzed = (List<Map<String, Object>>) response.getBody().get("data");
                    log.info("AI分析完成，返回 {} 条结果", analyzed != null ? analyzed.size() : 0);
                }
            } catch (Exception aiEx) {
                log.warn("AI服务调用失败，使用本地排序策略: {}", aiEx.getMessage());
            }
            
            // AI服务不可用时使用本地排序策略
            if (analyzed == null || analyzed.isEmpty()) {
                log.info("使用本地排序策略: 按年份+评分排序");
                contents.sort((a, b) -> {
                    int yearCompare = (b.getYear() != null ? b.getYear() : 0) - (a.getYear() != null ? a.getYear() : 0);
                    if (yearCompare != 0) return yearCompare;
                    BigDecimal ratingA = a.getRating() != null ? a.getRating() : BigDecimal.ZERO;
                    BigDecimal ratingB = b.getRating() != null ? b.getRating() : BigDecimal.ZERO;
                    return ratingB.compareTo(ratingA);
                });
                
                int newSortOrder = 0;
                for (HomepageContent content : contents) {
                    HomepageContent update = new HomepageContent();
                    update.setId(content.getId());
                    update.setSortOrder(newSortOrder++);
                    update.setAiScore(content.getRating());
                    update.setAiReason("按年份+评分排序");
                    updateById(update);
                }
            } else {
                // 使用AI分析结果更新
                int newSortOrder = 0;
                for (Map<String, Object> filmMap : analyzed) {
                    Long id = ((Number) filmMap.get("id")).longValue();
                    Double aiScore = filmMap.get("aiScore") instanceof Number 
                            ? ((Number) filmMap.get("aiScore")).doubleValue() : 50.0;
                    String aiReason = (String) filmMap.get("aiReason");
                    
                    HomepageContent update = new HomepageContent();
                    update.setId(id);
                    update.setAiScore(new BigDecimal(aiScore));
                    update.setAiReason(aiReason != null ? aiReason : "AI推荐");
                    update.setSortOrder(newSortOrder++);
                    updateById(update);
                }
                log.info("AI智能排序完成，已更新 {} 部电影的排序和分数", analyzed.size());
            }
            
            clearCache();
            
        } catch (Exception e) {
            log.error("智能排序失败: {}", e.getMessage(), e);
            throw new RuntimeException("排序失败: " + e.getMessage());
        }
    }

    @Override
    public void updateSortOrder(Long id, Integer sortOrder) {
        HomepageContent content = new HomepageContent();
        content.setId(id);
        content.setSortOrder(sortOrder);
        updateById(content);
        clearCache();
    }

    @Override
    public void toggleStatus(Long id) {
        HomepageContent content = getById(id);
        if (content != null) {
            content.setStatus(content.getStatus() == 1 ? 0 : 1);
            updateById(content);
            clearCache();
        }
    }

    private List<HomepageContentVO> getListBySectionType(String sectionType, Integer limit) {
        List<HomepageContent> list = homepageContentMapper.selectBySectionType(sectionType, limit);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    private List<HomepageContentVO> getListByContentType(String contentType, Integer limit) {
        List<HomepageContent> list = homepageContentMapper.selectByContentType(contentType, limit);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    private HomepageContentVO convertToVO(HomepageContent entity) {
        HomepageContentVO vo = new HomepageContentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private HomepageContentVO convertToVO(PublishedContent entity) {
        HomepageContentVO vo = new HomepageContentVO();
        BeanUtils.copyProperties(entity, vo);
        // 如果PublishedContent没有tag字段但VO需要，可以在这里处理
        return vo;
    }

    private List<HomepageContentVO> getPublishedList(String category, String sectionType, Integer limit) {
        try {
            HomepageConfigVersion version = configVersionMapper.selectLatestPublished(category);
            if (version != null) {
                // selectFrontendList 已经按 position 排序
                List<PublishedContent> list = publishedContentMapper.selectFrontendList(category, version.getVersion());
                return list.stream()
                        .filter(c -> sectionType == null || sectionType.equals(c.getSectionType()))
                        .limit(limit)
                        .map(this::convertToVO)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("获取已发布内容失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private void clearCache() {
        try {
            redisTemplate.delete(redisTemplate.keys(CACHE_KEY_PREFIX + "*"));
        } catch (Exception e) {
            log.warn("清除缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public List<HomepageContentVO> getAiBestList(Integer limit) {
        try {
            // 先尝试使用ai_best字段查询
            LambdaQueryWrapper<HomepageContent> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(HomepageContent::getStatus, 1)
                   .eq(HomepageContent::getDeleted, 0)
                   .orderByDesc(HomepageContent::getAiScore)
                   .last("LIMIT " + limit);
            List<HomepageContent> list = list(wrapper);
            // 过滤AI分数高的作为精选
            list = list.stream()
                    .filter(c -> c.getAiScore() != null && c.getAiScore().doubleValue() >= 70)
                    .collect(Collectors.toList());
            if (list.isEmpty()) {
                // 降级：使用高评分内容代替
                wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(HomepageContent::getStatus, 1)
                       .eq(HomepageContent::getDeleted, 0)
                       .orderByDesc(HomepageContent::getRating)
                       .last("LIMIT " + limit);
                list = list(wrapper);
            }
            return list.stream().map(this::convertToVO).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取AI精选列表失败: {}", e.getMessage());
            // 最终降级：返回推荐列表
            return getRecommendList(limit);
        }
    }

    @Override
    public List<HomepageContentVO> getNewList(Integer limit) {
        try {
            int currentYear = java.time.LocalDate.now().getYear();
            LambdaQueryWrapper<HomepageContent> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(HomepageContent::getYear, currentYear - 1)
                   .eq(HomepageContent::getStatus, 1)
                   .eq(HomepageContent::getDeleted, 0)
                   .orderByDesc(HomepageContent::getYear)
                   .orderByDesc(HomepageContent::getCreateTime)
                   .last("LIMIT " + limit);
            List<HomepageContent> list = list(wrapper);
            return list.stream().map(this::convertToVO).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取新片列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<HomepageContentVO> getTrendingList(Integer limit) {
        try {
            // 查询trending板块数据，不使用trending_score字段避免列不存在问题
            LambdaQueryWrapper<HomepageContent> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(HomepageContent::getSectionType, "trending")
                   .eq(HomepageContent::getStatus, 1)
                   .eq(HomepageContent::getDeleted, 0)
                   .orderByAsc(HomepageContent::getSortOrder)
                   .last("LIMIT " + limit);
            List<HomepageContent> list = list(wrapper);
            
            // 如果没有trending板块的数据，用热门评分高的代替
            if (list.isEmpty()) {
                wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(HomepageContent::getStatus, 1)
                       .eq(HomepageContent::getDeleted, 0)
                       .orderByDesc(HomepageContent::getRating)
                       .last("LIMIT " + limit);
                list = list(wrapper);
            }
            return list.stream().map(this::convertToVO).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取趋势列表失败: {}", e.getMessage());
            return getHotList(limit); // 降级返回热门列表
        }
    }

    @Override
    public Map<String, List<HomepageContentVO>> getSectionedContent() {
        Map<String, List<HomepageContentVO>> sections = new LinkedHashMap<>();
        
        // AI精选 (AI推荐值最高的)
        sections.put("ai_best", getAiBestList(6));
        
        // 热门推荐
        sections.put("hot", getHotList(12));
        
        // 新片上映
        sections.put("new", getNewList(12));
        
        // 趋势/话题
        sections.put("trending", getTrendingList(8));
        
        // 经典推荐
        sections.put("recommend", getRecommendList(12));
        
        return sections;
    }

    @Override
    public void markAsBest(Long id, boolean isBest) {
        HomepageContent content = new HomepageContent();
        content.setId(id);
        content.setAiBest(isBest ? 1 : 0);
        updateById(content);
        clearCache();
        log.info("已{}AI精选标记: id={}", isBest ? "添加" : "移除", id);
    }

    private void syncToLibrary(List<Map<String, Object>> films) {
        log.info("开始后台同步电影库，共 {} 部...", films.size());
        int count = 0;
        String proxyBaseUrl = "http://localhost:3001/api/tvbox/play/";
        
        for (Map<String, Object> film : films) {
            try {
                // 1. 复制基础数据
                Map<String, Object> data = new HashMap<>(film);
                
                // 2. 获取播放链接 (调用 Proxy Play 接口)
                String tvboxId = (String) film.get("id");
                if (tvboxId == null) continue;
                
                try {
                     ResponseEntity<Map> playRes = restTemplate.getForEntity(proxyBaseUrl + tvboxId, Map.class);
                     if (playRes.getBody() != null && playRes.getBody().get("data") != null) {
                         Map playInfo = (Map) playRes.getBody().get("data");
                         String playUrl = (String) playInfo.get("playUrl");
                         if (playUrl != null && !playUrl.isEmpty()) {
                             data.put("videoUrl", playUrl);
                         }
                     }
                } catch (Exception e) {
                    log.warn("获取播放链接失败: id={}, err={}", tvboxId, e.getMessage());
                }
                
                // 3. 保存到库
                if (filmService.saveFromTvbox(data)) {
                    count++;
                }
                
                // 避免请求过快
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                
            } catch (Exception e) {
                log.error("同步电影失败: {}", film.get("title"), e);
            }
        }
        log.info("后台同步完成，新增入库 {} 部电影", count);
    }
}
