package com.jelly.cinema.film.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.mapper.FilmMapper;
import com.jelly.cinema.film.service.FilmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 电影服务启动后自动从 TVBox 扫描一批数据，优先把常见内容灌入 MySQL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TvboxStartupSyncBootstrap {

    private final FilmMapper filmMapper;
    private final FilmService filmService;

    @Value("${tvbox.startup-sync.enabled:true}")
    private boolean enabled;

    @Value("${tvbox.startup-sync.initial-target:2000}")
    private int initialTarget;

    @Value("${tvbox.startup-sync.incremental-target:100}")
    private int incrementalTarget;

    @Value("${tvbox.startup-sync.delay-ms:5000}")
    private long delayMs;

    private final AtomicBoolean started = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled || !started.compareAndSet(false, true)) {
            return;
        }

        Thread worker = new Thread(this::runBootstrap, "tvbox-startup-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    private void runBootstrap() {
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }

            long currentCount = countAvailableFilms();
            boolean fullSweep = currentCount < initialTarget;
            int targetNewCount = fullSweep
                    ? Math.max(0, initialTarget - (int) currentCount)
                    : Math.max(0, incrementalTarget);

            if (targetNewCount <= 0 && !fullSweep) {
                log.info("TVBox 启动补库跳过: currentCount={}, initialTarget={}, incrementalTarget={}",
                        currentCount, initialTarget, incrementalTarget);
                return;
            }

            log.info("TVBox 启动补库开始: currentCount={}, fullSweep={}, targetNewCount={}",
                    currentCount, fullSweep, targetNewCount);

            int imported = filmService.warmupCatalogFromTvbox(targetNewCount, fullSweep);
            long finalCount = countAvailableFilms();

            log.info("TVBox 启动补库完成: imported={}, before={}, after={}", imported, currentCount, finalCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("TVBox 启动补库被中断");
        } catch (Exception e) {
            log.warn("TVBox 启动补库失败: {}", e.getMessage(), e);
        }
    }

    private long countAvailableFilms() {
        Long count = filmMapper.selectCount(
                new LambdaQueryWrapper<Film>()
                        .eq(Film::getStatus, 0)
        );
        return count == null ? 0L : count;
    }
}
