package com.jelly.cinema.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.entity.SensitiveWord;
import com.jelly.cinema.admin.mapper.SensitiveWordMapper;
import com.jelly.cinema.admin.service.SensitiveWordService;
import com.jelly.cinema.admin.util.SensitiveWordFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 敏感词服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordFilter filter = new SensitiveWordFilter();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    @Override
    public Page<SensitiveWord> page(int pageNum, int pageSize, String keyword, Integer type) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
        
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(SensitiveWord::getWord, keyword);
        }
        if (type != null) {
            wrapper.eq(SensitiveWord::getType, type);
        }
        
        wrapper.orderByDesc(SensitiveWord::getCreateTime);
        return sensitiveWordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void add(SensitiveWord word) {
        word.setStatus(1);
        sensitiveWordMapper.insert(word);
        refreshCache();
    }

    @Override
    public int batchAdd(List<String> words, Integer type, Integer strategy) {
        int count = 0;
        for (String word : words) {
            if (StrUtil.isBlank(word)) {
                continue;
            }
            
            // 检查是否已存在
            LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SensitiveWord::getWord, word.trim());
            if (sensitiveWordMapper.selectCount(wrapper) > 0) {
                continue;
            }
            
            SensitiveWord sw = new SensitiveWord();
            sw.setWord(word.trim());
            sw.setType(type != null ? type : SensitiveWord.TYPE_OTHER);
            sw.setStrategy(strategy != null ? strategy : SensitiveWord.STRATEGY_REPLACE);
            sw.setStatus(1);
            sensitiveWordMapper.insert(sw);
            count++;
        }
        
        if (count > 0) {
            refreshCache();
        }
        return count;
    }

    @Override
    public void delete(Integer id) {
        sensitiveWordMapper.deleteById(id);
        refreshCache();
    }

    @Override
    public void updateStatus(Integer id, Integer status) {
        SensitiveWord word = new SensitiveWord();
        word.setId(id);
        word.setStatus(status);
        sensitiveWordMapper.updateById(word);
        refreshCache();
    }

    @Override
    public SensitiveWordFilter.FilterResult filter(String text) {
        return filter.filter(text);
    }

    @Override
    public void refreshCache() {
        List<SensitiveWord> words = sensitiveWordMapper.selectEnabledWords();
        filter.init(words);
        log.info("敏感词缓存已刷新，共 {} 个", words.size());
    }
}
