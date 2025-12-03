package com.jelly.cinema.community.controller;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.community.domain.dto.PostCreateDTO;
import com.jelly.cinema.community.domain.vo.PostVO;
import com.jelly.cinema.community.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "帖子管理")
@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "分页查询帖子列表")
    @GetMapping("/list")
    public R<PageResult<PostVO>> list(
            PageQuery query,
            @Parameter(description = "搜索关键词") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "关联电影ID") @RequestParam(value = "filmId", required = false) Long filmId) {
        return R.ok(postService.list(query, keyword, filmId));
    }

    @Operation(summary = "获取帖子详情")
    @GetMapping("/detail/{id}")
    public R<PostVO> getDetail(@PathVariable Long id) {
        postService.incrementViewCount(id);
        return R.ok(postService.getDetail(id));
    }

    @Operation(summary = "发布帖子")
    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody PostCreateDTO dto) {
        return R.ok(String.valueOf(postService.create(dto)));
    }

    @Operation(summary = "删除帖子")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return R.ok();
    }

    @Operation(summary = "投票（赞同/反对）")
    @PostMapping("/vote/{id}")
    public R<Void> vote(
            @PathVariable Long id,
            @Parameter(description = "1-赞同，-1-反对，0-取消") @RequestParam(value = "type") Integer type) {
        postService.vote(id, type);
        return R.ok();
    }

    @Operation(summary = "获取我的帖子列表")
    @GetMapping("/my")
    public R<PageResult<PostVO>> listMyPosts(PageQuery query) {
        return R.ok(postService.listMyPosts(query));
    }
}
