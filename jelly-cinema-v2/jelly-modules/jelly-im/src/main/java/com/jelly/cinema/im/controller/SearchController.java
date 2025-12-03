package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.im.domain.vo.GroupSearchVO;
import com.jelly.cinema.im.domain.vo.UserSearchVO;
import com.jelly.cinema.im.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "社交搜索")
@RestController
@RequestMapping("/im/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "搜索用户")
    @GetMapping("/user")
    public R<List<UserSearchVO>> searchUser(@RequestParam String keyword) {
        return R.ok(searchService.searchUser(keyword));
    }

    @Operation(summary = "搜索群组")
    @GetMapping("/group")
    public R<List<GroupSearchVO>> searchGroup(@RequestParam String keyword) {
        return R.ok(searchService.searchGroup(keyword));
    }
}
