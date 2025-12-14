package com.jelly.cinema.auth.service;

import com.jelly.cinema.auth.domain.vo.PointLogVO;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;

public interface UserPointService {

    Integer getBalance();

    void addPoints(Long userId, int amount, int type, String remark);

    boolean deductPoints(Long userId, int amount, int type, String remark);

    PageResult<PointLogVO> getPointLogs(PageQuery query);
}
