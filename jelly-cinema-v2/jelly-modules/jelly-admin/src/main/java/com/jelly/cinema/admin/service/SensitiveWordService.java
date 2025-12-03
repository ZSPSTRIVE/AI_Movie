package com.jelly.cinema.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.entity.SensitiveWord;
import com.jelly.cinema.admin.util.SensitiveWordFilter;

import java.util.List;

/**
 * 敏感词服务
 *
 * @author Jelly Cinema
 */
public interface SensitiveWordService {

    /**
     * 分页查询敏感词
     */
    Page<SensitiveWord> page(int pageNum, int pageSize, String keyword, Integer type);

    /**
     * 添加敏感词
     */
    void add(SensitiveWord word);

    /**
     * 批量添加敏感词
     */
    int batchAdd(List<String> words, Integer type, Integer strategy);

    /**
     * 删除敏感词
     */
    void delete(Integer id);

    /**
     * 更新敏感词状态
     */
    void updateStatus(Integer id, Integer status);

    /**
     * 过滤文本
     */
    SensitiveWordFilter.FilterResult filter(String text);

    /**
     * 刷新敏感词缓存
     */
    void refreshCache();
}
