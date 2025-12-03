package com.jelly.cinema.admin.util;

import com.jelly.cinema.admin.domain.entity.SensitiveWord;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 敏感词过滤器 (DFA 算法实现)
 *
 * @author Jelly Cinema
 */
@Slf4j
public class SensitiveWordFilter {

    /**
     * DFA 状态机根节点
     */
    private final Map<Character, Object> rootMap = new HashMap<>();

    /**
     * 敏感词策略映射
     */
    private final Map<String, Integer> strategyMap = new HashMap<>();

    /**
     * 结束标志
     */
    private static final String END_FLAG = "isEnd";

    /**
     * 初始化敏感词库
     *
     * @param words 敏感词列表
     */
    public void init(List<SensitiveWord> words) {
        rootMap.clear();
        strategyMap.clear();
        
        if (words == null || words.isEmpty()) {
            return;
        }

        for (SensitiveWord sw : words) {
            String word = sw.getWord();
            if (word == null || word.isEmpty()) {
                continue;
            }
            
            strategyMap.put(word, sw.getStrategy());
            addWord(word);
        }
        
        log.info("敏感词库初始化完成，共加载 {} 个敏感词", words.size());
    }

    /**
     * 添加敏感词到 DFA 状态机
     */
    @SuppressWarnings("unchecked")
    private void addWord(String word) {
        Map<Character, Object> currentMap = rootMap;
        
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Object obj = currentMap.get(c);
            
            if (obj == null) {
                Map<Character, Object> newMap = new HashMap<>();
                newMap.put(END_FLAG.charAt(0), false);
                currentMap.put(c, newMap);
                currentMap = newMap;
            } else {
                currentMap = (Map<Character, Object>) obj;
            }
            
            // 最后一个字符，标记为结束
            if (i == word.length() - 1) {
                currentMap.put(END_FLAG.charAt(0), true);
            }
        }
    }

    /**
     * 检查文本中是否包含敏感词
     *
     * @param text 待检查文本
     * @return 包含的敏感词列表
     */
    public List<String> check(String text) {
        List<String> sensitiveWords = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return sensitiveWords;
        }

        for (int i = 0; i < text.length(); i++) {
            int length = checkWord(text, i);
            if (length > 0) {
                sensitiveWords.add(text.substring(i, i + length));
                i += length - 1; // 跳过已匹配的部分
            }
        }
        
        return sensitiveWords;
    }

    /**
     * 从指定位置开始检查敏感词
     *
     * @param text  文本
     * @param begin 起始位置
     * @return 敏感词长度，0表示不是敏感词
     */
    @SuppressWarnings("unchecked")
    private int checkWord(String text, int begin) {
        Map<Character, Object> currentMap = rootMap;
        int matchLength = 0;
        int lastMatchLength = 0;

        for (int i = begin; i < text.length(); i++) {
            char c = text.charAt(i);
            currentMap = (Map<Character, Object>) currentMap.get(c);
            
            if (currentMap == null) {
                break;
            }
            
            matchLength++;
            
            // 检查是否是敏感词结尾
            if (Boolean.TRUE.equals(currentMap.get(END_FLAG.charAt(0)))) {
                lastMatchLength = matchLength;
            }
        }
        
        return lastMatchLength;
    }

    /**
     * 过滤文本中的敏感词
     *
     * @param text 待过滤文本
     * @return 过滤结果
     */
    public FilterResult filter(String text) {
        FilterResult result = new FilterResult();
        result.setOriginalText(text);
        result.setHasSensitive(false);
        result.setBlockedWords(new ArrayList<>());
        
        if (text == null || text.isEmpty()) {
            result.setFilteredText(text);
            return result;
        }

        StringBuilder filtered = new StringBuilder(text);
        List<String> foundWords = check(text);
        
        if (foundWords.isEmpty()) {
            result.setFilteredText(text);
            return result;
        }
        
        result.setHasSensitive(true);
        
        for (String word : foundWords) {
            Integer strategy = strategyMap.get(word);
            
            // 策略2：直接拦截
            if (strategy != null && strategy == SensitiveWord.STRATEGY_BLOCK) {
                result.getBlockedWords().add(word);
                result.setShouldBlock(true);
            }
            
            // 替换为 ***
            String replacement = "***";
            int idx;
            while ((idx = filtered.indexOf(word)) != -1) {
                filtered.replace(idx, idx + word.length(), replacement);
            }
        }
        
        result.setFilteredText(filtered.toString());
        return result;
    }

    /**
     * 过滤结果
     */
    public static class FilterResult {
        private String originalText;
        private String filteredText;
        private boolean hasSensitive;
        private boolean shouldBlock;
        private List<String> blockedWords;

        public String getOriginalText() { return originalText; }
        public void setOriginalText(String originalText) { this.originalText = originalText; }
        
        public String getFilteredText() { return filteredText; }
        public void setFilteredText(String filteredText) { this.filteredText = filteredText; }
        
        public boolean isHasSensitive() { return hasSensitive; }
        public void setHasSensitive(boolean hasSensitive) { this.hasSensitive = hasSensitive; }
        
        public boolean isShouldBlock() { return shouldBlock; }
        public void setShouldBlock(boolean shouldBlock) { this.shouldBlock = shouldBlock; }
        
        public List<String> getBlockedWords() { return blockedWords; }
        public void setBlockedWords(List<String> blockedWords) { this.blockedWords = blockedWords; }
    }
}
