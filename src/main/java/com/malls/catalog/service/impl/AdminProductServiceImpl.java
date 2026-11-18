package com.malls.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.domain.Sku;
import com.malls.catalog.dto.ProductQuery;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.catalog.repository.ProductQueries;
import com.malls.catalog.service.AdminProductService;
import com.malls.catalog.service.CategoryService;
import com.malls.catalog.service.ProductDetailAssembler;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.ProductVO;
import com.malls.common.api.PageResult;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台商品管理实现。
 *
 * <p>三个要点（答辩会被问）：
 * <ol>
 *   <li><b>列表不加上架过滤</b>：复用 {@link ProductQueries#ofAdminQuery}，只把
 *       {@code onlyOnSale} 置为 false，检索条件与前台完全同源；</li>
 *   <li><b>详情绕开缓存</b>：直接调 {@link ProductDetailAssembler}，不经过
 *       {@code ProductService#detail}，将来前台加缓存也影响不到后台；</li>
 *   <li><b>上下架是条件更新</b>：{@code where id = ? and status <> '目标状态'}，
 *       并发双击时只有一次生效，不会出现"读旧值→写新值"的覆盖。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CategoryService categoryService;
    private final ProductDetailAssembler productDetailAssembler;

    @Override
    public PageResult<ProductVO> page(ProductQuery query, ProductStatus status) {
        List<Long> categoryIds = null;
        if (query.categoryId() != null) {
            categoryIds = categoryService.subtreeIds(query.categoryId());
        }
        // 关键差异：false —— 不加上架过滤，运营能看到下架商品
        LambdaQueryWrapper<Product> wrapper = ProductQueries.ofAdminQuery(query, categoryIds, status);
        ProductQueries.sorted(query.normalizeSort(), wrapper);

        Page<Product> productPage = productMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return PageResult.of(productPage, ProductVO::of);
    }

    @Override
    public ProductDetailVO detail(Long productId) {
        // 直连取数组件：不经过任何可能带缓存的 Service 方法
        return productDetailAssembler.assemble(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO onShelf(Long productId) {
        return transition(productId, ProductStatus.ON_SALE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO offShelf(Long productId) {
        return transition(productId, ProductStatus.OFF_SHELF);
    }

    /**
     * 状态流转的公共实现。
     *
     * <p>不挂在 {@code @Transactional} 公有方法上的原因：同一个 Bean 内部自调用不走代理，
     * 注解会失效；事务边界放在 {@link #onShelf}/{@link #offShelf} 上才真正生效。
     */
    private ProductVO transition(Long productId, ProductStatus target) {
        // @TableLogic 自动追加 deleted = 0，软删商品等同于不存在
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 库里若存在 NULL（历史脏数据），按"未上架"处理，避免 NPE
        ProductStatus current = product.getStatus() == null ? ProductStatus.OFF_SHELF : product.getStatus();
        if (current == target) {
            // 幂等：重复点击/重复请求不报错、不写库，直接把当前状态回给前端
            log.info("商品状态未变化，忽略本次操作 productId={} status={}", productId, target);
            return ProductVO.of(product);
        }

        if (target == ProductStatus.ON_SALE) {
            requireSellableSku(productId);
        }

        // 条件更新：只有"当前状态仍不是目标状态"时才写，天然防并发覆盖。
        // 用 update(null, wrapper) 而不是 updateById(product)：后者会把读出来的
        // sales/rating 等字段整体写回，覆盖掉并发产生的销量变化。
        int rows = productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                .set(Product::getStatus, target)
                .set(Product::getUpdateTime, LocalDateTime.now())
                .eq(Product::getId, productId)
                .ne(Product::getStatus, target));

        if (rows == 0) {
            // 并发者抢先改了：回读真实状态，绝不把过期状态回给前端
            Product latest = productMapper.selectById(productId);
            if (latest == null) {
                throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
            }
            log.info("并发状态变更已生效，回读真实状态 productId={} actual={}", productId, latest.getStatus());
            return ProductVO.of(latest);
        }

        product.setStatus(target);
        log.info("商品状态变更 productId={} {} -> {}", productId, current, target);
        return ProductVO.of(product);
    }

    /** 上架前置：至少要有一个可售 SKU，否则前端点进详情无货可买 */
    private void requireSellableSku(Long productId) {
        long enabledSkus = skuMapper.selectCount(Wrappers.<Sku>lambdaQuery()
                .eq(Sku::getProductId, productId)
                .eq(Sku::isEnabled, true));
        if (enabledSkus == 0) {
            throw new BizException(ResultCode.PRODUCT_STATUS_ILLEGAL, "上架前请先配置至少一个可用规格");
        }
    }
}
