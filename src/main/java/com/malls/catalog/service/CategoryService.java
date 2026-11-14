package com.malls.catalog.service;

import com.malls.catalog.vo.CategoryNode;

import java.util.List;

public interface CategoryService {
    List<CategoryNode> tree();
}
