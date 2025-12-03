package com.jelly.cinema.common.api.feign.fallback;

import com.jelly.cinema.common.api.domain.RemoteFriend;
import com.jelly.cinema.common.api.domain.RemoteGroup;
import com.jelly.cinema.common.api.domain.RemoteGroupSimple;
import com.jelly.cinema.common.api.domain.RemoteMessage;
import com.jelly.cinema.common.api.feign.RemoteImService;
import com.jelly.cinema.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * IM 服务降级处理
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
public class RemoteImFallback implements FallbackFactory<RemoteImService> {

    @Override
    public RemoteImService create(Throwable cause) {
        log.error("IM 服务调用失败: {}", cause.getMessage());

        return new RemoteImService() {
            @Override
            public R<List<RemoteGroup>> getGroups(int pageNum, int pageSize, String keyword) {
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<Long> getGroupCount() {
                return R.ok(0L);
            }

            @Override
            public R<Integer> getActiveGroupCount() {
                return R.ok(0);
            }

            @Override
            public R<Void> dismissGroup(Long groupId, String reason) {
                return R.fail("IM 服务不可用");
            }

            @Override
            public R<List<RemoteMessage>> getGroupMessages(Long groupId, int pageNum, int pageSize) {
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<Long> getMessageCount(Long groupId) {
                return R.ok(0L);
            }

            @Override
            public R<List<RemoteFriend>> getUserFriends(Long userId) {
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<List<RemoteGroupSimple>> getUserGroups(Long userId) {
                return R.ok(Collections.emptyList());
            }
        };
    }
}
