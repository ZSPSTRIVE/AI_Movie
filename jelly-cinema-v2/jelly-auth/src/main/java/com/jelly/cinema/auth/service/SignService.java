package com.jelly.cinema.auth.service;

import com.jelly.cinema.auth.domain.vo.SignStatusVO;

public interface SignService {

    boolean checkin();

    SignStatusVO getSignStatus();
}
