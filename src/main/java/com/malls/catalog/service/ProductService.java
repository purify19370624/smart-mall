package com.malls.catalog.service;

import com.malls.catalog.dto.ProductQuery;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.ProductVO;
import com.malls.common.api.PageResult;

import java.util.List;

public interface ProductService {
    PageResult<ProductVO> page(ProductQuery productQuery);
    List<Long> subtreeIds(Long categoryId);


    // 商品详情
    ProductDetailVO detail(Long productId);

}
