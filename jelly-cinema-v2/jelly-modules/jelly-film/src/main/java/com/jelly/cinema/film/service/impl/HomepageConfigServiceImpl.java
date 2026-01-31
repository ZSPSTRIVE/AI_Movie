package com.jelly.cinema.film.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;
import com.jelly.cinema.film.domain.entity.PublishedContent;
import com.jelly.cinema.film.mapper.HomepageConfigVersionMapper;
import com.jelly.cinema.film.mapper.PublishedContentMapper;
import com.jelly.cinema.film.service.HomepageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首页配置服务实现
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomepageConfigServiceImpl implements HomepageConfigService {

    private final HomepageConfigVersionMapper configVersionMapper;
    private final PublishedContentMapper publishedContentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long versionId) {
        HomepageConfigVersion version = configVersionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("配置版本不存在");
        }
        if ("published".equals(version.getStatus())) {
            throw new RuntimeException("该版本已发布");
        }

        // 1. 归档旧版本
        configVersionMapper.archivePreviousCategoryVersions(version.getCategory(), versionId);

        // 2. 更新状态
        version.setStatus("published");
        version.setPublishedAt(LocalDateTime.now());
        configVersionMapper.updateById(version);

        // 3. 更新 t_published_content 表（供前台快速读取）
        updatePublishedContent(version);
        
        log.info("配置版本已发布: {}", version.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long versionId) {
        // 回滚逻辑：创建一个新版本，内容复制自目标版本
        HomepageConfigVersion target = configVersionMapper.selectById(versionId);
        if (target == null) {
            throw new RuntimeException("目标版本不存在");
        }

        HomepageConfigVersion rollbackVersion = new HomepageConfigVersion();
        rollbackVersion.setCategory(target.getCategory());
        rollbackVersion.setVersion(generateNextVersion(target.getCategory())); // 生成新版本号
        rollbackVersion.setStatus("draft");
        rollbackVersion.setConfigJson(target.getConfigJson());
        rollbackVersion.setRollbackFrom(target.getVersion());
        rollbackVersion.setPublishNote("Rollback from " + target.getVersion());
        configVersionMapper.insert(rollbackVersion);
        
        // 自动发布
        publish(rollbackVersion.getId());
    }

    private void updatePublishedContent(HomepageConfigVersion version) {
        // 1. 解析JSON
        List<Map<String, Object>> slotsConfig;
        try {
            slotsConfig = objectMapper.readValue(version.getConfigJson(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("配置格式错误", e);
        }

        // 2. 清除旧数据的部分
        publishedContentMapper.deleteOldVersions(version.getCategory(), version.getVersion());
        
        // 3. 插入新数据
        for (Map<String, Object> slot : slotsConfig) {
            if ("filled".equals(slot.get("status"))) {
                Map<String, Object> content = (Map<String, Object>) slot.get("content");
                if (content == null) continue;

                PublishedContent pc = new PublishedContent();
                pc.setConfigVersion(version.getVersion());
                pc.setCategory(version.getCategory());
                pc.setSectionType((String) slot.get("section_type"));
                pc.setSlotId((String) slot.get("slot_id"));
                pc.setPosition((Integer) slot.get("position"));
                pc.setLocked((Integer) slot.get("locked"));
                pc.setStatus(1);

                pc.setCanonicalId((String) content.get("canonical_id"));
                pc.setTvboxId((String) content.get("tvbox_id"));
                pc.setTitle((String) content.get("title"));
                pc.setCoverUrl((String) content.get("cover_url"));
                pc.setPlayUrl((String) content.get("play_url"));
                pc.setSourceName((String) content.get("source_name"));
                
                // 类型转换安全处理
                pc.setYear(content.get("year") instanceof Integer ? (Integer) content.get("year") : null);
                pc.setRegion((String) content.get("region"));
                
                Object rating = content.get("rating");
                if (rating instanceof BigDecimal) pc.setRating((BigDecimal) rating);
                else if (rating instanceof Number) pc.setRating(new BigDecimal(rating.toString()));

                publishedContentMapper.insert(pc);
            }
        }
    }
    
    // 复用DraftGenerator中的版本生成逻辑，实际应提取到工具类或Service方法
    private String generateNextVersion(String category) {
        return "v" + System.currentTimeMillis(); 
    }
}
