package com.jelly.cinema.film.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.mapper.FilmMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 电影数据同步任务
 * 
 * 功能：
 * 1. 全量同步：定期将所有电影数据同步到 ES
 * 2. 增量同步：同步最近更新的数据
 * 
 * 可配合 XXL-Job 使用
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilmDataSyncJob {

    private final FilmMapper filmMapper;
    private final FilmSearchService filmSearchService;

    /**
     * 每页大小
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 上次同步时间
     */
    private LocalDateTime lastSyncTime;

    /**
     * 全量同步（每天凌晨 3 点执行）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void fullSync() {
        log.info("===== 开始全量同步电影数据到 ES =====");
        long startTime = System.currentTimeMillis();

        try {
            int totalCount = 0;
            int pageNum = 1;

            while (true) {
                // 分页查询
                Page<Film> page = filmMapper.selectPage(
                        new Page<>(pageNum, BATCH_SIZE),
                        new LambdaQueryWrapper<Film>().eq(Film::getStatus, 1)
                );

                List<Film> films = page.getRecords();
                if (films.isEmpty()) {
                    break;
                }

                // 批量索引
                filmSearchService.bulkIndexFilms(films);
                totalCount += films.size();

                log.info("全量同步进度: page={}, count={}, total={}", 
                        pageNum, films.size(), totalCount);

                if (films.size() < BATCH_SIZE) {
                    break;
                }

                pageNum++;
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("===== 全量同步完成: total={}, cost={}ms =====", totalCount, costTime);

        } catch (Exception e) {
            log.error("全量同步失败", e);
        }
    }

    /**
     * 增量同步（每 10 分钟执行一次）
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void incrementalSync() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime syncFrom = lastSyncTime != null ? lastSyncTime : now.minusMinutes(10);

        log.info("===== 开始增量同步: from={} =====", syncFrom);

        try {
            // 查询最近更新的电影
            LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Film::getStatus, 1);
            wrapper.gt(Film::getUpdateTime, syncFrom);

            List<Film> films = filmMapper.selectList(wrapper);

            if (!films.isEmpty()) {
                filmSearchService.bulkIndexFilms(films);
                log.info("增量同步完成: count={}", films.size());
            } else {
                log.debug("无需同步的数据");
            }

            lastSyncTime = now;

        } catch (Exception e) {
            log.error("增量同步失败", e);
        }
    }

    /**
     * 手动触发全量同步
     */
    public void triggerFullSync() {
        new Thread(this::fullSync, "manual-full-sync").start();
    }

    /**
     * 同步单个电影
     */
    public void syncFilm(Long filmId) {
        try {
            Film film = filmMapper.selectById(filmId);
            if (film != null && film.getStatus() == 1) {
                filmSearchService.indexFilm(film);
                log.info("同步电影成功: id={}, title={}", film.getId(), film.getTitle());
            }
        } catch (Exception e) {
            log.error("同步电影失败: filmId={}", filmId, e);
        }
    }
}
