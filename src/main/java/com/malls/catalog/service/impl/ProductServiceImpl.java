package com.malls.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.malls.catalog.domain.Brand;
import com.malls.catalog.domain.Category;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.Sku;
import com.malls.catalog.dto.ProductQuery;
import com.malls.catalog.mapper.BrandMapper;
import com.malls.catalog.mapper.CategoryMapper;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.catalog.repository.ProductQueries;
import com.malls.catalog.service.CategoryService;
import com.malls.catalog.service.ProductService;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.ProductVO;
import com.malls.catalog.vo.SkuVO;
import com.malls.common.api.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j                     // 生成 log
@Service                   // 注册为业务组件
@RequiredArgsConstructor   // 构造器注入
public class ProductServiceImpl implements ProductService {
    // 商品表 Mapper：SPU 的查询与更新
    private final ProductMapper productMapper;

    private final CategoryMapper categoryMapper;

    private final BrandMapper brandMapper;
    private final SkuMapper skuMapper;
    // 分类服务：注入接口而不是实现类，以后换实现本类不用改
    private final CategoryService categoryService;
    // 分页

    @Override
    public PageResult<ProductVO> page(ProductQuery query) {
        List<Long> categoryIds = null;
        if(query.categoryId()!=null){
            List<Long> longs = subtreeIds(query.categoryId());
        }
        LambdaQueryWrapper<Product> wrapper = ProductQueries.ofQuery(query, categoryIds, true);
        ProductQueries.sorted(query.normalizeSort(),wrapper);
        Page<Product> productPage = productMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);

        return PageResult.of(productPage,ProductVO::of);
    }

    @Override
    public List<Long> subtreeIds(Long categoryId) {
        if(categoryId == null){

            return List.of();
        }
        List<Category> all = categoryMapper.selectList(null);
        List<Long> result = new ArrayList<>();
        result.add(categoryId);
        collectChildren(categoryId,all,result);
        return result;

    }

    private void collectChildren(Long parentId, List<Category> all, List<Long> result) {
        for (Category category : all) {
            if (parentId.equals(category.getParentId())) {
                result.add(category.getId());
                collectChildren(category.getId(),all,result);
            }
        }
    }

    @Override
    public ProductDetailVO detail(Long productId) {
        Product product = productMapper.selectById(productId);
        List<SkuVO> skuList = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getProductId, productId)
                .eq(Sku::isEnabled, true)
                .orderByAsc(Sku::getPrice)
                .orderByAsc(Sku::getId)).stream().map(SkuVO::of).toList();
        String  brandName ="平台自营";
        if(product.getBrandId()!=null){
            Brand brand = brandMapper.selectById(product.getBrandId());
            if (brand!=null) {
                brandName = brand.getName();
            }

        }

        return ProductDetailVO.of(product,null,brandName,skuList);
    }


}
