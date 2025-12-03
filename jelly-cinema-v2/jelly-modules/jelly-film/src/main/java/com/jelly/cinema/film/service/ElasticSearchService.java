package com.jelly.cinema.film.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jelly.cinema.film.domain.entity.Film;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 检索服务
 * 兼容 ES 7.6 版本，向量检索使用 script_score 方式
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticSearchService {

    private final RestHighLevelClient restHighLevelClient;

    private static final String FILM_INDEX = "jelly_film";

    /**
     * 索引电影文档
     *
     * @param film     电影实体
     * @param embedding 向量嵌入（可选）
     */
    public void indexFilm(Film film, float[] embedding) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", film.getId());
            doc.put("title", film.getTitle());
            doc.put("description", film.getDescription());
            doc.put("director", film.getDirector());
            doc.put("actors", film.getActors());
            doc.put("tags", film.getTags());
            doc.put("rating", film.getRating());
            doc.put("playCount", film.getPlayCount());
            doc.put("year", film.getYear());

            // 如果有向量，添加到文档
            if (embedding != null) {
                doc.put("embedding", embedding);
            }

            IndexRequest request = new IndexRequest(FILM_INDEX)
                    .id(String.valueOf(film.getId()))
                    .source(doc, XContentType.JSON);

            restHighLevelClient.index(request, RequestOptions.DEFAULT);
            log.debug("索引电影成功: {}", film.getTitle());
        } catch (IOException e) {
            log.error("索引电影失败: {}", film.getTitle(), e);
        }
    }

    /**
     * 关键词搜索电影
     *
     * @param keyword 关键词
     * @param size    返回数量
     * @return 电影 ID 列表
     */
    public List<Long> searchByKeyword(String keyword, int size) {
        List<Long> ids = new ArrayList<>();
        if (StrUtil.isBlank(keyword)) {
            return ids;
        }

        try {
            SearchRequest searchRequest = new SearchRequest(FILM_INDEX);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                    .should(QueryBuilders.matchQuery("title", keyword).boost(3.0f))
                    .should(QueryBuilders.matchQuery("description", keyword).boost(1.0f))
                    .should(QueryBuilders.matchQuery("actors", keyword).boost(2.0f))
                    .should(QueryBuilders.matchQuery("director", keyword).boost(2.0f));

            sourceBuilder.query(boolQuery);
            sourceBuilder.size(size);
            searchRequest.source(sourceBuilder);

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                if (source.get("id") != null) {
                    ids.add(Long.valueOf(source.get("id").toString()));
                }
            }
        } catch (IOException e) {
            log.error("ES 搜索失败: {}", keyword, e);
        }

        return ids;
    }

    /**
     * 向量相似度检索（ES 7.6 script_score 方式）
     * 使用余弦相似度计算
     *
     * @param queryVector 查询向量
     * @param size        返回数量
     * @return 电影 ID 列表
     */
    public List<Long> searchByVector(float[] queryVector, int size) {
        List<Long> ids = new ArrayList<>();
        if (queryVector == null || queryVector.length == 0) {
            return ids;
        }

        try {
            SearchRequest searchRequest = new SearchRequest(FILM_INDEX);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            // ES 7.6 向量检索：使用 script_score + cosineSimilarity
            Map<String, Object> params = new HashMap<>();
            params.put("query_vector", queryVector);

            Script script = new Script(
                    ScriptType.INLINE,
                    "painless",
                    "cosineSimilarity(params.query_vector, 'embedding') + 1.0",
                    params
            );

            sourceBuilder.query(QueryBuilders.scriptScoreQuery(
                    QueryBuilders.matchAllQuery(),
                    script
            ));
            sourceBuilder.size(size);
            searchRequest.source(sourceBuilder);

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                if (source.get("id") != null) {
                    ids.add(Long.valueOf(source.get("id").toString()));
                }
            }
        } catch (IOException e) {
            log.error("ES 向量检索失败", e);
        }

        return ids;
    }

    /**
     * 混合检索：关键词 + 向量重排序
     *
     * @param keyword     关键词
     * @param queryVector 查询向量
     * @param size        返回数量
     * @return 电影 ID 列表
     */
    public List<Long> hybridSearch(String keyword, float[] queryVector, int size) {
        // 先用关键词召回
        List<Long> keywordResults = searchByKeyword(keyword, size * 2);

        // 如果没有向量，直接返回关键词结果
        if (queryVector == null || queryVector.length == 0) {
            return keywordResults.stream().limit(size).toList();
        }

        // 有向量时，结合向量检索结果
        List<Long> vectorResults = searchByVector(queryVector, size);

        // 简单融合：去重后返回
        List<Long> merged = new ArrayList<>(keywordResults);
        for (Long id : vectorResults) {
            if (!merged.contains(id)) {
                merged.add(id);
            }
        }

        return merged.stream().limit(size).toList();
    }
}
