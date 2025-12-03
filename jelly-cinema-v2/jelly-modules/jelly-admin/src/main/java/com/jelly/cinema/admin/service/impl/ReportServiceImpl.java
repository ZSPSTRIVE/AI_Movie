package com.jelly.cinema.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.entity.AdminLog;
import com.jelly.cinema.admin.domain.entity.Report;
import com.jelly.cinema.admin.domain.vo.ReportVO;
import com.jelly.cinema.admin.mapper.AdminLogMapper;
import com.jelly.cinema.admin.mapper.ReportMapper;
import com.jelly.cinema.admin.service.ReportService;
import com.jelly.cinema.admin.service.UserManageService;
import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 举报服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final AdminLogMapper adminLogMapper;
    private final RemoteUserService remoteUserService;
    @Lazy
    private final UserManageService userManageService;

    @Override
    public Page<ReportVO> page(int pageNum, int pageSize, Integer status, Integer targetType) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null) {
            wrapper.eq(Report::getStatus, status);
        }
        if (targetType != null) {
            wrapper.eq(Report::getTargetType, targetType);
        }
        
        wrapper.orderByDesc(Report::getCreateTime);
        
        Page<Report> page = reportMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        
        // 转换为 VO
        Page<ReportVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        
        return voPage;
    }

    @Override
    public ReportVO getDetail(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new ServiceException("举报记录不存在");
        }
        return toVO(report);
    }

    @Override
    public void handle(Long id, int action, String feedback) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new ServiceException("举报记录不存在");
        }
        if (report.getStatus() != Report.STATUS_PENDING) {
            throw new ServiceException("该举报已处理");
        }

        Long handlerId = LoginHelper.getUserId();
        
        // 更新举报状态
        report.setStatus(action == 1 ? Report.STATUS_IGNORED : Report.STATUS_HANDLED);
        report.setHandlerId(handlerId);
        report.setHandleTime(LocalDateTime.now());
        
        String result;
        switch (action) {
            case 1 -> result = "已忽略";
            case 2 -> result = "已警告被举报方";
            case 3 -> result = "已封禁被举报方";
            default -> result = "已处理";
        }
        report.setResult(result + (feedback != null ? ": " + feedback : ""));
        
        reportMapper.updateById(report);

        // 如果 action == 3，调用封禁服务封禁被举报用户
        if (action == 3 && report.getTargetType() == Report.TARGET_USER) {
            try {
                // 封禁用户 24 小时，可根据业务需要调整
                userManageService.banUser(report.getTargetId(), 24, "因举报处理被封禁: " + report.getReason());
                log.info("用户 {} 因举报被封禁", report.getTargetId());
            } catch (Exception e) {
                log.error("封禁用户失败: {}", e.getMessage());
            }
        }

        // 记录操作日志
        AdminLog adminLog = new AdminLog();
        adminLog.setAdminId(handlerId);
        adminLog.setModule(AdminLog.MODULE_CONTENT);
        adminLog.setAction("处理举报");
        adminLog.setTargetId(id);
        adminLog.setTargetType("report");
        adminLog.setDetail("动作: " + result + ", 反馈: " + feedback);
        adminLogMapper.insert(adminLog);

        log.info("举报 {} 已处理，动作: {}", id, result);
    }

    @Override
    public int getPendingCount() {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getStatus, Report.STATUS_PENDING);
        return Math.toIntExact(reportMapper.selectCount(wrapper));
    }

    private ReportVO toVO(Report report) {
        ReportVO vo = BeanUtil.copyProperties(report, ReportVO.class);
        
        // 解析证据图片 JSON
        if (report.getEvidenceImgs() != null) {
            try {
                vo.setEvidenceImgs(JSONUtil.toList(report.getEvidenceImgs(), String.class));
            } catch (Exception e) {
                vo.setEvidenceImgs(List.of());
            }
        }
        
        // 查询举报人信息
        try {
            R<RemoteUser> reporterResult = remoteUserService.getUserById(report.getReporterId());
            if (reporterResult.isSuccess() && reporterResult.getData() != null) {
                RemoteUser reporter = reporterResult.getData();
                vo.setReporterNickname(reporter.getNickname());
                vo.setReporterAvatar(reporter.getAvatar());
            } else {
                vo.setReporterNickname("用户" + report.getReporterId());
            }
        } catch (Exception e) {
            vo.setReporterNickname("用户" + report.getReporterId());
        }
        
        // 根据目标类型查询目标信息
        if (report.getTargetType() == Report.TARGET_USER) {
            // 用户类型
            try {
                R<RemoteUser> targetResult = remoteUserService.getUserById(report.getTargetId());
                if (targetResult.isSuccess() && targetResult.getData() != null) {
                    RemoteUser target = targetResult.getData();
                    vo.setTargetName(target.getNickname());
                    vo.setTargetAvatar(target.getAvatar());
                } else {
                    vo.setTargetName("用户" + report.getTargetId());
                }
            } catch (Exception e) {
                vo.setTargetName("用户" + report.getTargetId());
            }
        } else if (report.getTargetType() == Report.TARGET_GROUP) {
            // 群组类型（简化处理）
            vo.setTargetName("群组" + report.getTargetId());
        } else {
            vo.setTargetName("目标" + report.getTargetId());
        }
        
        return vo;
    }
}
