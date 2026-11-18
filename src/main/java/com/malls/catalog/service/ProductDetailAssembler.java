package com.malls.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.malls.catalog.domain.Brand;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.Sku;
import com.malls.catalog.mapper.BrandMapper;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.SkuVO;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品详情取数：SPU 本体 + 可售 SKU + 品牌名的<b>唯一</b>拼装入口。
 *
 * <p>为什么单独抽成一个组件，而不是放在 Service 里写两个方法：
 * <ul>
 *   <li><b>共用</b>：前台详情与后台详情必须看到同一份数据口径，
 *       否则"后台看到 3 个 SKU、前台只看到 2 个"这类问题永远对不上账；</li>
 *   <li><b>可绕开缓存</b>：后台详情要求"看最新"，直接调本组件即可。
 *       将来若给前台 {@code ProductService#detail} 加 {@code @Cacheable}，
 *       缓存只挂在 Service 上，本组件依旧每次直连数据库；
 *       而且同一 Bean 内部自调用会绕过 Spring 代理，所以缓存绝不能加在这里。</li>
 * </ul>
 *
 * <p>职责边界：<b>只取数，不判业务可见性</b>。上下架属于展示策略，由调用方决定
 * （前台可拒绝下架商品，后台必须能看到）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductDetailAssembler {

    /** 无品牌时的兜底展示名 */
    private static final String SELF_OPERATED = "平台自营";

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final BrandMapper brandMapper;

    /**
     * 拼装详情。
     *
     * @param productId 商品 ID
     * @return 可直接渲染的详情视图
     * @throws BizException 商品不存在或已逻辑删除时抛 {@code PRODUCT_NOT_FOUND}
     */
    public ProductDetailVO assemble(Long productId) {
        // @TableLogic 会自动追加 deleted = 0，软删商品查不到
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 详情页只展示可售 SKU，按价格升序让"最便宜的那档"自然排在第一位
        List<SkuVO> skuList = skuMapper.selectList(Wrappers.<Sku>lambdaQuery()
                        .eq(Sku::getProductId, productId)
                        .eq(Sku::isEnabled, true)
                        .orderByAsc(Sku::getPrice)
                        .orderByAsc(Sku::getId))
                .stream()
                .map(SkuVO::of)
                .toList();

        return ProductDetailVO.of(product, null, brandNameOf(product), skuList);
    }

    /** 品牌名：没配品牌就落到「平台自营」，前端不必判空 */
    private String brandNameOf(Product product) {
        if (product.getBrandId() == null) {
            return SELF_OPERATED;
        }
        Brand brand = brandMapper.selectById(product.getBrandId());
        return brand == null || brand.getName() == null || brand.getName().isBlank()
                ? SELF_OPERATED
                : brand.getName();
    }
}
