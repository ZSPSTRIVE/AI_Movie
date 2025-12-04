package com.jelly.cinema.film.search;

import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.domain.vo.FilmVO;
import com.jelly.cinema.film.handler.SentinelFallbackHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

/**
 * 电影搜索服务（增强版）
 * 
 * 功能：
 * 1. 全文搜索（倒排索引）
 * 2. 多字段 Boost 权重
 * 3. IK 分词 + 同义词支持
 * 4. 搜索建议（自动补全）
 * 5. 高亮显示
 * 6. 分类/标签过滤
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilmSearchService {

    private final RestHighLevelClient restHighLevelClient;

    private static final String FILM_INDEX = "jelly_film_v2";

    /**
     * 字段权重配置
     */
    private static final float TITLE_BOOST = 5.0f;
    private static final float ACTORS_BOOST = 3.0f;
    private static final float DIRECTOR_BOOST = 3.0f;
    private static final float TAGS_BOOST = 2.0f;
    private static final float DESCRIPTION_BOOST = 1.0f;

    @PostConstruct
    public void init() {
        try {
            createIndexIfNotExists();
        } catch (Exception e) {
            log.warn("初始化 ES 索引失败: {}", e.getMessage());
        }
    }

    /**
     * 创建索引（如果不存在）
     */
    private void createIndexIfNotExists() throws IOException {
        GetIndexRequest getRequest = new GetIndexRequest(FILM_INDEX);
        boolean exists = restHighLevelClient.indices().exists(getRequest, RequestOptions.DEFAULT);

        if (!exists) {
            createFilmIndex();
        }
    }

    /**
     * 创建电影索引
     * 
     * 索引配置：
     * - IK 分词器（ik_max_word / ik_smart）
     * - 同义词过滤器
     * - 拼音搜索支持
     */
    private void createFilmIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(FILM_INDEX);

        // 索引设置
        request.settings(Settings.builder()
                .put("number_of_shards", 3)
                .put("number_of_replicas", 1)
                .put("analysis.analyzer.ik_synonym.type", "custom")
                .put("analysis.analyzer.ik_synonym.tokenizer", "ik_max_word")
                .put("analysis.analyzer.ik_synonym.filter", "synonym_filter")
                .put("analysis.filter.synonym_filter.type", "synonym")
                .put("analysis.filter.synonym_filter.synonyms_path", "analysis/synonyms.txt")
        );

        // 映射配置
        XContentBuilder mapping = XContentFactory.jsonBuilder();
        mapping.startObject();
        {
            mapping.startObject("properties");
            {
                // 电影 ID
                mapping.startObject("id");
                mapping.field("type", "long");
                mapping.endObject();

                // 标题（支持 IK 分词和关键词）
                mapping.startObject("title");
                mapping.field("type", "text");
                mapping.field("analyzer", "ik_max_word");
                mapping.field("search_analyzer", "ik_smart");
                mapping.startObject("fields");
                {
                    mapping.startObject("keyword");
                    mapping.field("type", "keyword");
                    mapping.endObject();
                    
                    // 搜索建议字段
                    mapping.startObject("suggest");
                    mapping.field("type", "completion");
                    mapping.field("analyzer", "ik_max_word");
                    mapping.endObject();
                }
                mapping.endObject();
                mapping.endObject();

                // 描述
                mapping.startObject("description");
                mapping.field("type", "text");
                mapping.field("analyzer", "ik_max_word");
                mapping.endObject();

                // 导演
                mapping.startObject("director");
                mapping.field("type", "text");
                mapping.field("analyzer", "ik_max_word");
                mapping.startObject("fields");
                mapping.startObject("keyword");
                mapping.field("type", "keyword");
                mapping.endObject();
                mapping.endObject();
                mapping.endObject();

                // 演员
                mapping.startObject("actors");
                mapping.field("type", "text");
                mapping.field("analyzer", "ik_max_word");
                mapping.endObject();

                // 标签
                mapping.startObject("tags");
                mapping.field("type", "text");
                mapping.field("analyzer", "ik_max_word");
                mapping.startObject("fields");
                mapping.startObject("keyword");
                mapping.field("type", "keyword");
                mapping.endObject();
                mapping.endObject();
                mapping.endObject();

                // 分类 ID
                mapping.startObject("categoryId");
                mapping.field("type", "long");
                mapping.endObject();

                // 年份
                mapping.startObject("year");
                mapping.field("type", "integer");
                mapping.endObject();

                // 评分
                mapping.startObject("rating");
                mapping.field("type", "float");
                mapping.endObject();

                // 播放量
                mapping.startObject("playCount");
                mapping.field("type", "long");
                mapping.endObject();

                // 发布时间
                mapping.startObject("releaseTime");
                mapping.field("type", "date");
                mapping.endObject();

                // 创建时间
                mapping.startObject("createTime");
                mapping.field("type", "date");
                mapping.endObject();
            }
            mapping.endObject();
        }
        mapping.endObject();

        request.mapping(mapping);

        restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
        log.info("创建电影索引成功: {}", FILM_INDEX);
    }

    /**
     * 索引电影文档
     */
    public void indexFilm(Film film) {
        try {
            Map<String, Object> doc = buildDocument(film);

            IndexRequest request = new IndexRequest(FILM_INDEX)
                    .id(String.valueOf(film.getId()))
                    .source(doc, XContentType.JSON);

            restHighLevelClient.index(request, RequestOptions.DEFAULT);
            log.debug("索引电影: id={}, title={}", film.getId(), film.getTitle());
        } catch (IOException e) {
            log.error("索引电影失败: {}", film.getTitle(), e);
        }
    }

    /**
     * 批量索引电影
     */
    public void bulkIndexFilms(List<Film> films) {
        if (films == null || films.isEmpty()) {
            return;
        }

        try {
            BulkRequest bulkRequest = new BulkRequest();

            for (Film film : films) {
                Map<String, Object> doc = buildDocument(film);
                IndexRequest indexRequest = new IndexRequest(FILM_INDEX)
                        .id(String.valueOf(film.getId()))
                        .source(doc, XContentType.JSON);
                bulkRequest.add(indexRequest);
            }

            BulkResponse response = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            log.info("批量索引完成: 成功={}, 失败={}", 
                    films.size() - response.getItems().length, 
                    response.hasFailures() ? "有失败" : "无失败");
        } catch (IOException e) {
            log.error("批量索引失败", e);
        }
    }

    /**
     * 构建文档
     */
    private Map<String, Object> buildDocument(Film film) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", film.getId());
        doc.put("title", film.getTitle());
        doc.put("description", film.getDescription());
        doc.put("director", film.getDirector());
        doc.put("actors", film.getActors());
        doc.put("tags", film.getTags());
        doc.put("categoryId", film.getCategoryId());
        doc.put("year", film.getYear());
        doc.put("rating", film.getRating());
        doc.put("playCount", film.getPlayCount());
        doc.put("createTime", film.getCreateTime());
        return doc;
    }

    /**
     * 全文搜索
     * 
     * @param keyword 搜索关键词
     * @param categoryId 分类 ID（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 搜索结果
     */
    @SentinelResource(value = "searchFilm",
            blockHandler = "searchFilmFallback",
            blockHandlerClass = SentinelFallbackHandler.class)
    public SearchResult search(String keyword, Long categoryId, int page, int size) {
        SearchResult result = new SearchResult();
        result.setKeyword(keyword);

        if (StrUtil.isBlank(keyword)) {
            return result;
        }

        try {
            SearchRequest searchRequest = new SearchRequest(FILM_INDEX);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            // 构建查询
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

            // 多字段匹配（带权重）
            MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery(keyword)
                    .field("title", TITLE_BOOST)
                    .field("actors", ACTORS_BOOST)
                    .field("director", DIRECTOR_BOOST)
                    .field("tags", TAGS_BOOST)
                    .field("description", DESCRIPTION_BOOST)
                    .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                    .minimumShouldMatch("50%");

            boolQuery.must(multiMatch);

            // 分类过滤
            if (categoryId != null) {
                boolQuery.filter(QueryBuilders.termQuery("categoryId", categoryId));
            }

            sourceBuilder.query(boolQuery);

            // 分页
            sourceBuilder.from((page - 1) * size);
            sourceBuilder.size(size);

            // 排序：相关性 + 评分 + 播放量
            sourceBuilder.sort("_score", SortOrder.DESC);
            sourceBuilder.sort("rating", SortOrder.DESC);
            sourceBuilder.sort("playCount", SortOrder.DESC);

            // 高亮
            HighlightBuilder highlightBuilder = new HighlightBuilder()
                    .field("title")
                    .field("description")
                    .preTags("<em class='highlight'>")
                    .postTags("</em>");
            sourceBuilder.highlighter(highlightBuilder);

            searchRequest.source(sourceBuilder);

            // 执行搜索
            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 解析结果
            result.setTotal(response.getHits().getTotalHits().value);
            result.setTook(response.getTook().getMillis());

            List<SearchResultItem> items = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                SearchResultItem item = new SearchResultItem();
                Map<String, Object> source = hit.getSourceAsMap();

                item.setId(Long.valueOf(source.get("id").toString()));
                item.setTitle((String) source.get("title"));
                item.setDescription((String) source.get("description"));
                item.setScore(hit.getScore());

                // 处理高亮
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                if (highlightFields.containsKey("title")) {
                    item.setHighlightTitle(highlightFields.get("title").fragments()[0].string());
                }
                if (highlightFields.containsKey("description")) {
                    item.setHighlightDescription(highlightFields.get("description").fragments()[0].string());
                }

                items.add(item);
            }
            result.setItems(items);

            log.debug("搜索完成: keyword={}, total={}, took={}ms", keyword, result.getTotal(), result.getTook());

        } catch (IOException e) {
            log.error("搜索失败: keyword={}", keyword, e);
        }

        return result;
    }

    /**
     * 搜索建议（自动补全）
     */
    public List<String> suggest(String prefix, int size) {
        List<String> suggestions = new ArrayList<>();

        if (StrUtil.isBlank(prefix)) {
            return suggestions;
        }

        try {
            SearchRequest searchRequest = new SearchRequest(FILM_INDEX);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            // 补全建议
            SuggestBuilder suggestBuilder = new SuggestBuilder()
                    .addSuggestion("title-suggest",
                            SuggestBuilders.completionSuggestion("title.suggest")
                                    .prefix(prefix)
                                    .size(size)
                                    .skipDuplicates(true));

            sourceBuilder.suggest(suggestBuilder);
            searchRequest.source(sourceBuilder);

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            Suggest suggest = response.getSuggest();
            if (suggest != null) {
                CompletionSuggestion completionSuggestion = suggest.getSuggestion("title-suggest");
                if (completionSuggestion != null) {
                    for (CompletionSuggestion.Entry.Option option : completionSuggestion.getOptions()) {
                        suggestions.add(option.getText().string());
                    }
                }
            }

        } catch (IOException e) {
            log.error("搜索建议失败: prefix={}", prefix, e);
        }

        return suggestions;
    }

    /**
     * 按标签搜索
     */
    public List<Long> searchByTags(List<String> tags, int size) {
        List<Long> ids = new ArrayList<>();

        if (tags == null || tags.isEmpty()) {
            return ids;
        }

        try {
            SearchRequest searchRequest = new SearchRequest(FILM_INDEX);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            for (String tag : tags) {
                boolQuery.should(QueryBuilders.matchQuery("tags", tag));
            }
            boolQuery.minimumShouldMatch(1);

            sourceBuilder.query(boolQuery);
            sourceBuilder.size(size);
            sourceBuilder.sort("rating", SortOrder.DESC);

            searchRequest.source(sourceBuilder);

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                if (source.get("id") != null) {
                    ids.add(Long.valueOf(source.get("id").toString()));
                }
            }

        } catch (IOException e) {
            log.error("按标签搜索失败: tags={}", tags, e);
        }

        return ids;
    }

    /**
     * 相似电影搜索（基于标签和描述）
     */
    public List<Long> findSimilar(Long filmId, String tags, String description, int size) {
        List<Long> ids = new ArrayList<>();

        try {
            SearchRequest searchRequest = new SearchRequest(FILM_INDEX);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

            // 排除当前电影
            boolQuery.mustNot(QueryBuilders.termQuery("id", filmId));

            // 标签匹配
            if (StrUtil.isNotBlank(tags)) {
                boolQuery.should(QueryBuilders.matchQuery("tags", tags).boost(2.0f));
            }

            // 描述相似
            if (StrUtil.isNotBlank(description)) {
                boolQuery.should(QueryBuilders.matchQuery("description", description));
            }

            sourceBuilder.query(boolQuery);
            sourceBuilder.size(size);
            sourceBuilder.sort("_score", SortOrder.DESC);
            sourceBuilder.sort("rating", SortOrder.DESC);

            searchRequest.source(sourceBuilder);

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                if (source.get("id") != null) {
                    ids.add(Long.valueOf(source.get("id").toString()));
                }
            }

        } catch (IOException e) {
            log.error("相似搜索失败: filmId={}", filmId, e);
        }

        return ids;
    }

    // ==================== 内部类 ====================

    /**
     * 搜索结果
     */
    @lombok.Data
    public static class SearchResult {
        private String keyword;
        private long total;
        private long took;
        private List<SearchResultItem> items = new ArrayList<>();
    }

    /**
     * 搜索结果项
     */
    @lombok.Data
    public static class SearchResultItem {
        private Long id;
        private String title;
        private String description;
        private float score;
        private String highlightTitle;
        private String highlightDescription;
    }
}
