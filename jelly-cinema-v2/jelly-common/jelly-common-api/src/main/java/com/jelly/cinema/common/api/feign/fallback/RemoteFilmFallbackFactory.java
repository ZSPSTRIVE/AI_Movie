package com.jelly.cinema.common.api.feign.fallback;

import com.jelly.cinema.common.api.domain.RemoteFilm;
import com.jelly.cinema.common.api.feign.RemoteFilmService;
import com.jelly.cinema.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 电影服务熔断降级工厂
 * 当电影服务不可用时，返回空结果或默认值，防止级联故障
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Component
public class RemoteFilmFallbackFactory implements FallbackFactory<RemoteFilmService> {

    @Override
    public RemoteFilmService create(Throwable cause) {
        log.error("电影服务调用失败: {}", cause.getMessage());
        
        return new RemoteFilmService() {
            
            @Override
            public R<RemoteFilm> getFilmById(Long id) {
                log.warn("Fallback: getFilmById({})", id);
                return R.fail("电影服务暂时不可用，请稍后重试");
            }

            @Override
            public R<List<RemoteFilm>> searchFilms(String keyword) {
                log.warn("Fallback: searchFilms({})", keyword);
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<List<RemoteFilm>> getRecommendFilms(Integer size) {
                log.warn("Fallback: getRecommendFilms({})", size);
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<List<RemoteFilm>> getHotFilms(Integer size) {
                log.warn("Fallback: getHotFilms({})", size);
                return R.ok(Collections.emptyList());
            }
        };
    }
}
