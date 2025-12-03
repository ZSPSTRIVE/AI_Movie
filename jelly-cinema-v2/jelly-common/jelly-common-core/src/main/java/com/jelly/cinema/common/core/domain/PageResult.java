package com.jelly.cinema.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果
 *
 * @param <T> 数据类型
 * @author Jelly Cinema
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据列表
     */
    private List<T> rows;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页数量
     */
    private Integer pageSize;

    /**
     * 总页数
     */
    private Integer pages;

    public PageResult(List<T> rows, Long total) {
        this.rows = rows;
        this.total = total;
    }

    public PageResult(List<T> rows, Long total, Integer pageNum, Integer pageSize) {
        this.rows = rows;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (int) Math.ceil((double) total / pageSize);
    }

    /**
     * 构建分页结果
     */
    public static <T> PageResult<T> build(List<T> rows, Long total, Integer pageNum, Integer pageSize) {
        return new PageResult<>(rows, total, pageNum, pageSize);
    }
}
