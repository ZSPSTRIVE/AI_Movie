package com.jelly.cinema.film.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.film.domain.entity.Category;
import com.jelly.cinema.film.mapper.CategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "分类管理")
@RestController
@RequestMapping("/film/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;

    @Operation(summary = "获取所有分类")
    @GetMapping("/list")
    public R<List<Category>> list() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getStatus, 0);
        wrapper.orderByAsc(Category::getSort);
        return R.ok(categoryMapper.selectList(wrapper));
    }
}
