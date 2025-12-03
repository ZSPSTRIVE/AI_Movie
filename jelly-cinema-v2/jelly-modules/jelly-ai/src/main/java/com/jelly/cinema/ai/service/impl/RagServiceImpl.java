package com.jelly.cinema.ai.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.jelly.cinema.ai.domain.entity.KnowledgeDoc;
import com.jelly.cinema.ai.mapper.KnowledgeDocMapper;
import com.jelly.cinema.ai.service.RagService;
import com.jelly.cinema.common.core.exception.ServiceException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final EmbeddingModel embeddingModel;
    private final RestHighLevelClient restHighLevelClient;

    private static final String INDEX_NAME = "jelly_knowledge";
    private static final int CHUNK_SIZE = 500;  // 每个分片的字符数
    private static final int CHUNK_OVERLAP = 50; // 分片重叠

    @Override
    public Long uploadDocument(MultipartFile file) {
        try {
            // 解析文档
            Tika tika = new Tika();
            String content;
            try (InputStream is = file.getInputStream()) {
                content = tika.parseToString(is);
            }

            if (content == null || content.isBlank()) {
                throw new ServiceException("文档内容为空");
            }

            // 保存文档记录
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setDocName(file.getOriginalFilename());
            doc.setDocType(FileUtil.extName(file.getOriginalFilename()));
            doc.setStatus(0); // 解析中
            knowledgeDocMapper.insert(doc);

            // 分片并向量化
            List<String> chunks = splitToChunks(content);
            doc.setChunkCount(chunks.size());

            int successCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                try {
                    String chunk = chunks.get(i);
                    
                    // 向量化
                    Embedding embedding = embeddingModel.embed(chunk).content();
                    float[] vector = embedding.vector();

                    // 存入 ES
                    Map<String, Object> esDoc = new HashMap<>();
                    esDoc.put("doc_id", doc.getId());
                    esDoc.put("chunk_index", i);
                    esDoc.put("content", chunk);
                    esDoc.put("embedding", vector);

                    IndexRequest request = new IndexRequest(INDEX_NAME)
                            .id(doc.getId() + "_" + i)
                            .source(esDoc, XContentType.JSON);
                    restHighLevelClient.index(request, RequestOptions.DEFAULT);
                    successCount++;
                } catch (Exception e) {
                    log.error("向量化分片失败: docId={}, chunkIndex={}", doc.getId(), i, e);
                }
            }

            // 更新状态
            doc.setStatus(successCount == chunks.size() ? 1 : 2);
            doc.setChunkCount(successCount);
            knowledgeDocMapper.updateById(doc);

            log.info("文档上传完成: docId={}, chunks={}", doc.getId(), successCount);
            return doc.getId();
        } catch (Exception e) {
            log.error("文档上传失败", e);
            throw new ServiceException("文档上传失败: " + e.getMessage());
        }
    }

    @Override
    public String retrieve(String query) {
        return retrieve(query, 3);
    }

    @Override
    public String retrieve(String query, int topK) {
        try {
            // 向量化查询
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            float[] queryVector = queryEmbedding.vector();

            // ES 7.6 向量检索：使用 script_score + cosineSimilarity
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

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
            sourceBuilder.size(topK);
            searchRequest.source(sourceBuilder);

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 组装上下文
            List<String> contexts = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                String content = (String) source.get("content");
                if (content != null) {
                    contexts.add(content);
                }
            }

            return String.join("\n\n---\n\n", contexts);
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return "";
        }
    }

    /**
     * 文本分片
     */
    private List<String> splitToChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            
            // 尝试在句号、问号、感叹号处断开
            if (end < length) {
                int lastSentenceEnd = -1;
                for (int i = end; i > start + CHUNK_SIZE / 2; i--) {
                    char c = text.charAt(i);
                    if (c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                        lastSentenceEnd = i + 1;
                        break;
                    }
                }
                if (lastSentenceEnd > 0) {
                    end = lastSentenceEnd;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = end - CHUNK_OVERLAP;
        }

        return chunks;
    }
}
