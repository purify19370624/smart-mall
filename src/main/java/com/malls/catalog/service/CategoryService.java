package com.malls.catalog.service;

import com.malls.catalog.vo.CategoryNode;

import java.util.List;

public interface CategoryService {

    /** 完整分类树（前台分类导航用） */
    List<CategoryNode> tree();

    /**
     * 取某分类及其所有子孙分类的 ID。
     *
     * <p>放在分类服务里而不是商品服务里：这是"分类树"的知识，
     * 商品检索只是它的一个调用方（前台列表、后台列表都要用）。
     */
    List<Long> subtreeIds(Long categoryId);
}
