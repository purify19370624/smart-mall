package com.malls.catalog.service;

import com.malls.catalog.domain.Product;
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

    // 单独查询商品的实体  按id查询（购物车与订单快照要用完整字段，而不是 VO）
    Product getProduct(Long productId);

}
