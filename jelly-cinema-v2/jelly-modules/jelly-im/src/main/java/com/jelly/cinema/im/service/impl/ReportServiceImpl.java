package com.jelly.cinema.im.service.impl;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.im.domain.dto.ReportDTO;
import com.jelly.cinema.im.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 举报服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void submit(Long userId, ReportDTO dto) {
        // 将举报记录写入数据库（跨模块，直接写入 t_report 表）
        String sql = "INSERT INTO t_report (id, reporter_id, target_id, target_type, reason, evidence_imgs, status, create_time) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 0, ?)";
        
        long id = System.currentTimeMillis();
        String evidenceJson = dto.getEvidenceImgs() != null ? JSONUtil.toJsonStr(dto.getEvidenceImgs()) : null;
        
        jdbcTemplate.update(sql,
                id,
                userId,
                dto.getTargetId(),
                dto.getTargetType(),
                dto.getReason(),
                evidenceJson,
                LocalDateTime.now()
        );
        
        log.info("用户 {} 举报了 {} (type={})", userId, dto.getTargetId(), dto.getTargetType());
    }
}
