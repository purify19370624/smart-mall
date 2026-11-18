package com.malls.catalog.service;

import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.dto.ProductQuery;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.ProductVO;
import com.malls.common.api.PageResult;

/**
 * 后台商品管理（运营/管理员的视角）。
 *
 * <p>与前台 {@link ProductService} 刻意分开：
 * <ul>
 *   <li>前台列表<b>永远</b>只出上架商品；后台列表要能看到下架商品</li>
 *   <li>前台详情将来会加缓存；后台详情必须看最新的</li>
 * </ul>
 * 两条口径混在同一个类里，早晚会出现"为了后台改一行、把前台改坏"的事故。
 */
public interface AdminProductService {

    /**
     * 后台列表：不加上架过滤。
     *
     * @param query  与前台一致的检索条件（关键词、分类、品牌、价格、排序、分页）
     * @param status 可选的状态精确过滤；null 表示上架 + 下架都要
     */
    PageResult<ProductVO> page(ProductQuery query, ProductStatus status);

    /** 后台详情：走 {@link ProductDetailAssembler} 的取数逻辑，绕开任何缓存 */
    ProductDetailVO detail(Long productId);

    /** 上架：OFF_SHELF → ON_SALE，幂等 */
    ProductVO onShelf(Long productId);

    /** 下架：ON_SALE → OFF_SHELF，幂等 */
    ProductVO offShelf(Long productId);
}
