package com.jelly.cinema.ai.service.impl;

import com.jelly.cinema.ai.service.RagService;
import com.jelly.cinema.ai.tools.PythonRagClient;
import com.jelly.cinema.common.api.feign.RemoteFilmService;
import com.jelly.cinema.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * RAG 检索服务实现
 *
 * 对外继续保留 Java Controller / Service 入口，
 * 但核心 ingest/search 已统一交给 Python RAG 服务处理。
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final PythonRagClient pythonRagClient;
    private final RemoteFilmService remoteFilmService;
    private final Tika tika = new Tika();

    @Override
    public Long uploadDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }

        try (InputStream is = file.getInputStream()) {
            String content = tika.parseToString(is);
            if (content == null || content.isBlank()) {
                throw new ServiceException("文档内容为空");
            }

            String title = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "uploaded-document"
                    : file.getOriginalFilename();
            Long documentId = pythonRagClient.ingest(
                    title,
                    content,
                    "general",
                    "java_upload",
                    title
            );
            if (documentId == null) {
                throw new ServiceException("Python RAG 服务暂时不可用");
            }

            log.info("文档上传完成: title={}, documentId={}", title, documentId);
            return documentId;
        } catch (ServiceException e) {
            throw e;
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
            prewarmFilmLibrary(query);
            String result = pythonRagClient.search(query, topK);
            return (result == null || result.isBlank()) ? "知识库中未找到足够依据。" : result;
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return "知识库服务暂时不可用。";
        }
    }

    private void prewarmFilmLibrary(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        try {
            remoteFilmService.searchFilms(query.trim());
        } catch (Exception e) {
            log.debug("Prewarm film library failed: query={}, err={}", query, e.getMessage());
        }
    }
}
