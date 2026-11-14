package com.malls.catalog.controller;

import com.malls.catalog.service.CategoryService;
import com.malls.catalog.vo.CategoryNode;
import com.malls.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    //查看商品的完整分类
    @GetMapping("/tree")
    public Result<List<CategoryNode>>getTree(){
        return Result.ok(categoryService.tree());
    }
}
