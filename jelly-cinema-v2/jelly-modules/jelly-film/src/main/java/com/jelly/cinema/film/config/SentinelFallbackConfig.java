package com.jelly.cinema.film.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务级 Sentinel 熔断降级配置
 * 
 * 熔断策略：
 * 1. 慢调用比例 (SLOW_REQUEST_RATIO)
 * 2. 异常比例 (ERROR_RATIO)
 * 3. 异常数 (ERROR_COUNT)
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Configuration
public class SentinelFallbackConfig {

    /**
     * 启用 @SentinelResource 注解支持
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initSentinelRules() {
        log.info("===== 初始化 Film 服务 Sentinel 规则 =====");
        
        // 初始化限流规则
        initFlowRules();
        
        // 初始化熔断降级规则
        initDegradeRules();
        
        // 初始化系统保护规则
        initSystemRules();
        
        log.info("===== Film 服务 Sentinel 规则初始化完成 =====");
    }

    /**
     * 服务级限流规则
     */
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 电影详情接口：50 QPS
        FlowRule filmDetailRule = new FlowRule();
        filmDetailRule.setResource("getFilmDetail");
        filmDetailRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        filmDetailRule.setCount(50);
        filmDetailRule.setLimitApp("default");
        rules.add(filmDetailRule);

        // 电影搜索接口：30 QPS
        FlowRule filmSearchRule = new FlowRule();
        filmSearchRule.setResource("searchFilm");
        filmSearchRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        filmSearchRule.setCount(30);
        filmSearchRule.setLimitApp("default");
        rules.add(filmSearchRule);

        // 电影推荐接口：20 QPS（计算密集型）
        FlowRule filmRecommendRule = new FlowRule();
        filmRecommendRule.setResource("recommendFilm");
        filmRecommendRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        filmRecommendRule.setCount(20);
        filmRecommendRule.setLimitApp("default");
        rules.add(filmRecommendRule);

        // 电影列表接口：100 QPS
        FlowRule filmListRule = new FlowRule();
        filmListRule.setResource("listFilm");
        filmListRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        filmListRule.setCount(100);
        filmListRule.setLimitApp("default");
        rules.add(filmListRule);

        // 热点电影接口：200 QPS（高频访问）
        FlowRule hotFilmRule = new FlowRule();
        hotFilmRule.setResource("getHotFilm");
        hotFilmRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        hotFilmRule.setCount(200);
        hotFilmRule.setLimitApp("default");
        rules.add(hotFilmRule);

        FlowRuleManager.loadRules(rules);
        log.info("加载 {} 条服务级限流规则", rules.size());
    }

    /**
     * 熔断降级规则
     * 
     * 熔断策略说明：
     * - SLOW_REQUEST_RATIO (0): 慢调用比例，当慢调用比例超过阈值时触发熔断
     * - ERROR_RATIO (1): 异常比例，当异常比例超过阈值时触发熔断
     * - ERROR_COUNT (2): 异常数，当异常数超过阈值时触发熔断
     */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 电影详情接口 - 慢调用熔断
        // 当 RT > 200ms 的请求比例超过 50%，触发熔断 30 秒
        DegradeRule filmDetailDegradeRule = new DegradeRule();
        filmDetailDegradeRule.setResource("getFilmDetail");
        filmDetailDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT); // 慢调用比例
        filmDetailDegradeRule.setCount(200); // RT 阈值 200ms
        filmDetailDegradeRule.setSlowRatioThreshold(0.5); // 慢调用比例阈值 50%
        filmDetailDegradeRule.setMinRequestAmount(10); // 最小请求数
        filmDetailDegradeRule.setStatIntervalMs(1000); // 统计时长 1 秒
        filmDetailDegradeRule.setTimeWindow(30); // 熔断时长 30 秒
        rules.add(filmDetailDegradeRule);

        // 电影搜索接口 - 异常比例熔断
        // 当异常比例超过 30%，触发熔断 60 秒
        DegradeRule filmSearchDegradeRule = new DegradeRule();
        filmSearchDegradeRule.setResource("searchFilm");
        filmSearchDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO); // 异常比例
        filmSearchDegradeRule.setCount(0.3); // 异常比例阈值 30%
        filmSearchDegradeRule.setMinRequestAmount(10); // 最小请求数
        filmSearchDegradeRule.setStatIntervalMs(1000); // 统计时长 1 秒
        filmSearchDegradeRule.setTimeWindow(60); // 熔断时长 60 秒
        rules.add(filmSearchDegradeRule);

        // ES 服务 - 异常数熔断
        // 当 1 分钟内异常数超过 5 次，触发熔断 60 秒
        DegradeRule esDegradeRule = new DegradeRule();
        esDegradeRule.setResource("elasticSearchService");
        esDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT); // 异常数
        esDegradeRule.setCount(5); // 异常数阈值
        esDegradeRule.setMinRequestAmount(5); // 最小请求数
        esDegradeRule.setStatIntervalMs(60000); // 统计时长 1 分钟
        esDegradeRule.setTimeWindow(60); // 熔断时长 60 秒
        rules.add(esDegradeRule);

        // 推荐服务 - 慢调用熔断
        DegradeRule recommendDegradeRule = new DegradeRule();
        recommendDegradeRule.setResource("recommendFilm");
        recommendDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        recommendDegradeRule.setCount(500); // RT 阈值 500ms
        recommendDegradeRule.setSlowRatioThreshold(0.5);
        recommendDegradeRule.setMinRequestAmount(5);
        recommendDegradeRule.setStatIntervalMs(1000);
        recommendDegradeRule.setTimeWindow(30);
        rules.add(recommendDegradeRule);

        DegradeRuleManager.loadRules(rules);
        log.info("加载 {} 条熔断降级规则", rules.size());
    }

    /**
     * 系统保护规则
     * 
     * 当系统指标超过阈值时，触发系统保护
     * - highestSystemLoad: 系统负载阈值
     * - highestCpuUsage: CPU 使用率阈值
     * - avgRt: 平均响应时间阈值
     * - maxThread: 最大并发线程数
     * - qps: 入口 QPS 阈值
     */
    private void initSystemRules() {
        List<SystemRule> rules = new ArrayList<>();

        SystemRule systemRule = new SystemRule();
        systemRule.setHighestSystemLoad(5.0); // 系统负载阈值（参考值，可根据实际情况调整）
        systemRule.setHighestCpuUsage(0.9); // CPU 使用率 90%
        systemRule.setAvgRt(100); // 平均响应时间 100ms
        systemRule.setMaxThread(500); // 最大并发线程数
        systemRule.setQps(500); // 入口 QPS
        rules.add(systemRule);

        SystemRuleManager.loadRules(rules);
        log.info("加载 {} 条系统保护规则", rules.size());
    }
}
