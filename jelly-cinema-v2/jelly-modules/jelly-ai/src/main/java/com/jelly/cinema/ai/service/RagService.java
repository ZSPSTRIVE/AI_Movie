package com.jelly.cinema.ai.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * RAG 检索服务接口
 *
 * @author Jelly Cinema
 */
public interface RagService {

    /**
     * 上传文档并进行向量化
     *
     * @param file 文档文件
     * @return 文档 ID
     */
    Long uploadDocument(MultipartFile file);

    /**
     * 检索相关文档
     *
     * @param query 查询内容
     * @return 相关上下文
     */
    String retrieve(String query);

    /**
     * 检索相关文档（带分数）
     *
     * @param query 查询内容
     * @param topK  返回数量
     * @return 相关上下文
     */
    String retrieve(String query, int topK);
}
