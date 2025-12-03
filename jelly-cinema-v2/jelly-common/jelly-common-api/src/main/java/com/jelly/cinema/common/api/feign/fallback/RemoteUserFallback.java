package com.jelly.cinema.common.api.feign.fallback;

import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 用户服务降级处理
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
public class RemoteUserFallback implements FallbackFactory<RemoteUserService> {

    @Override
    public RemoteUserService create(Throwable cause) {
        log.error("用户服务调用失败: {}", cause.getMessage());
        
        return new RemoteUserService() {
            @Override
            public R<RemoteUser> getUserById(Long id) {
                return R.fail("用户服务不可用");
            }

            @Override
            public R<List<RemoteUser>> getUsersByIds(List<Long> ids) {
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<List<RemoteUser>> searchUsers(String keyword) {
                return R.ok(Collections.emptyList());
            }
        };
    }
}
