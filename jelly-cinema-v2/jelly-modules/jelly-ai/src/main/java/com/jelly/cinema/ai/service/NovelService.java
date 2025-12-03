package com.jelly.cinema.ai.service;

import com.jelly.cinema.ai.domain.dto.NovelChapterDTO;
import com.jelly.cinema.ai.domain.dto.NovelOutlineDTO;
import reactor.core.publisher.Flux;

/**
 * AI 小说生成服务接口
 *
 * @author Jelly Cinema
 */
public interface NovelService {

    /**
     * 生成小说大纲
     *
     * @param dto 请求参数
     * @return 大纲 JSON
     */
    String generateOutline(NovelOutlineDTO dto);

    /**
     * 生成章节内容（流式）
     *
     * @param dto 请求参数
     * @return 章节内容流
     */
    Flux<String> generateChapter(NovelChapterDTO dto);

    /**
     * 获取小说上下文
     *
     * @param bookId 书籍 ID
     * @return 上下文摘要
     */
    String getNovelContext(String bookId);

    /**
     * 保存小说上下文
     *
     * @param bookId  书籍 ID
     * @param context 上下文内容
     */
    void saveNovelContext(String bookId, String context);
}
