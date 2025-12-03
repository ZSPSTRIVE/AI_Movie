package com.jelly.cinema.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_doc")
public class KnowledgeDoc extends BaseEntity {

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 文档类型：pdf/word/txt
     */
    private String docType;

    /**
     * 状态：0-解析中，1-向量化完成，2-失败
     */
    private Integer status;

    /**
     * 文件 URL
     */
    private String fileUrl;

    /**
     * 分片数量
     */
    private Integer chunkCount;
}
