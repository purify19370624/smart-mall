package com.malls.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.malls.catalog.domain.Product;
import com.malls.catalog.dto.ProductQuery;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.repository.ProductQueries;
import com.malls.catalog.service.CategoryService;
import com.malls.catalog.service.ProductDetailAssembler;
import com.malls.catalog.service.ProductService;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.ProductVO;
import com.malls.common.api.PageResult;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 前台商品查询。
 *
 * <p>这里只做"顾客视角"的事：列表永远只出上架商品，详情直接复用
 * {@link ProductDetailAssembler}（后台走同一个取数组件，保证口径一致）。
 * 后台的"看全部、上下架"在 {@code AdminProductServiceImpl} 里，两者互不污染。
 */
@Slf4j                     // 生成 log
@Service                   // 注册为业务组件
@RequiredArgsConstructor   // 构造器注入
public class ProductServiceImpl implements ProductService {

    // 商品表 Mapper：SPU 的查询
    private final ProductMapper productMapper;

    // 分类服务：注入接口而不是实现类，以后换实现本类不用改
    private final CategoryService categoryService;

    // 详情拼装的唯一入口，前后台共用
    private final ProductDetailAssembler productDetailAssembler;

    @Override
    public PageResult<ProductVO> page(ProductQuery query) {
        // 传一级分类时要连带所有子孙分类的商品；不传就完全不加分类条件
        List<Long> categoryIds = null;
        if (query.categoryId() != null) {
            categoryIds = categoryService.subtreeIds(query.categoryId());
        }
        // onlyOnSale = true：前台列表永远看不到下架商品
        LambdaQueryWrapper<Product> wrapper = ProductQueries.ofQuery(query, categoryIds, true);
        ProductQueries.sorted(query.normalizeSort(), wrapper);
        Page<Product> productPage = productMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);

        return PageResult.of(productPage, ProductVO::of);
    }

    /** 保留原接口：老调用方仍可按商品服务拿分类子树，实际实现已归位到分类服务 */
    @Override
    public List<Long> subtreeIds(Long categoryId) {
        return categoryService.subtreeIds(categoryId);
    }

    /**
     * 前台详情。
     *
     * <p>取数逻辑全部在 {@link ProductDetailAssembler#assemble} 里：
     * 未来给本方法加缓存注解时，后台详情不受影响——它直接调 assembler。
     */
    @Override
    public ProductDetailVO detail(Long productId) {
        return productDetailAssembler.assemble(productId);
    }

    /**
     * 按 ID 查商品实体（购物车、订单快照要用完整字段，而不是给前端看的 VO）。
     *
     * <p>查不到直接抛 12001，调用方不用判空。
     */
    @Override
    public Product getProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }
}
