package com.malls.cart.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.malls.cart.dto.AddCartItemRequest;
import com.malls.cart.service.CartService;
import com.malls.cart.vo.CartItemVO;
import com.malls.cart.vo.CartVO;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.Sku;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.catalog.service.ProductService;
import com.malls.catalog.service.SkuStockService;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import com.malls.order.domain.CartItem;
import com.malls.order.mapper.CartItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CartServiceImpl implements CartService {


    private final SkuMapper skuMapper;

    private final SkuStockService skuStockService ;
    private final ProductService productService;

    // 购物车表 Mapper：单表 CRUD  CartItemMapper
    private final CartItemMapper cartItemMapper;

    public CartServiceImpl(SkuMapper skuMapper, SkuStockService skuStockService, ProductService productService, CartItemMapper cartItemMapper) {
        this.skuMapper = skuMapper;
        this.skuStockService = skuStockService;
        this.productService = productService;
        this.cartItemMapper = cartItemMapper;
    }


    //   已经存在
    //   库存要够
    //   出现库存不够的情况  不能是负数  不能比库存小 == 商品相等

    @Override
    public CartItemVO addCartItem(Long userId, AddCartItemRequest request) {

        // 查询商品的规格
        Sku sku = getSku(userId);

        // 不能 停用 不能售完
        boolean available = sku.available();
        if (!available) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF, "该规格已停用或已售罄");
        }

        // 3. 商品 SPU 是否在售（详情页下架后不该还能加购）
        Product product = productService.getProduct(userId);
        if (!product.onSale()) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }

        // 本次的数量是否超过库存
        if (!sku.hasStock(request.quantity())) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF,"库存不足，当前仅剩 %d 件".formatted(sku.getStock()));
        }
        // 查是否已在车里
        CartItem cartItem = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, request.skuId()));

        if (cartItem != null) {
            // 已经在购物车里了 累加 商品的数量
            // merged 当前商品的数量
            int merged = cartItem.getQuantity() + request.quantity();
            // 当前商品的数量是否充足
            if (!sku.hasStock(merged)) {
                throw new BizException(ResultCode.PRODUCT_OFF_SHELF,"库存不足，当前仅剩 %d 件".formatted(sku.getStock()));
            }
            cartItem.setQuantity(merged);
            cartItem.setChecked(true);
            cartItemMapper.updateById(cartItem);

            return toVO(cartItem);
        }

        //  购物车里没有这个商品  新的商品
        CartItem cartItem1 = new CartItem();
        cartItem1.setUserId(userId);                             // 归属只能来自令牌
        cartItem1.setProductId(sku.getProductId());              // 从 SKU 反查，不信前端传的
        cartItem1.setSkuId(sku.getId());
        cartItem1.setQuantity(request.quantity());
        cartItem1.setChecked(true);

        // 购物车的数据库中新增一个商品
        cartItemMapper.insert(cartItem1);
        log.debug("加购成功 userId={} skuId={} qty={}", userId, sku.getId(), request.quantity());
        return toVO(cartItem1);
    }

    @Override
    public void checkAll(Long userId, boolean checked) {

        cartItemMapper.update(
                null,Wrappers.<CartItem>lambdaUpdate().
                        eq(CartItem::getUserId, userId).set(CartItem::isChecked, checked));
    }

    @Override
    public List<CartItem> listChecked(Long userId) {

        // 只返回勾选的，按加购顺序（下单明细按用户加购顺序更好读）
        return cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::isChecked, true)
                .orderByAsc(CartItem::getId));
    }

    @Override
    public CartVO CartListItem(Long userId) {

        List<CartItem> rows = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getId));
        List<CartItemVO> items = new ArrayList<>(rows.size());
        int totalQuantity = 0;                                  // 角标：所有条目数量之和
        int checkedQuantity = 0;                                // 勾选且可买的数量
        BigDecimal checkedAmount = BigDecimal.ZERO;             // 勾选且可买的金额
        for (CartItem item : rows) {
            // 组合显示的对象vo
            CartItemVO vo = toVO(item);
            // 将商品加入购物车
            items.add(vo);
            // 总数+1 购物车的数量+1
            totalQuantity += item.getQuantity();
            if (vo.checked() && vo.available()) {
               checkedQuantity += item.getQuantity();
                // BigDecimal 不可变：必须把 add 的结果写回去，否则累加永远是 0
                checkedAmount = checkedAmount.add(vo.subtotal());
            }
        }
        // 全选框状态：非空且每条都勾了才算全选、
        boolean allChecked = !items.isEmpty() && items.stream().allMatch(CartItemVO::checked);
        return new CartVO(items, totalQuantity, checkedQuantity, checkedAmount, allChecked);
    }

    @Override
    public void removeCartItem(Long userId, Long id) {

        // 逻辑删除也可以  先判断userId  id值在数据库当中的商品是否是一致的
        getOwned(userId, id);
        cartItemMapper.deleteById(id);

    }

    // 清空购物车
    @Override
    public void clear(Long userId) {
        if (userId==null || userId==0) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(
                CartItem::getUserId, userId
        ));
    }

    @Override
    public void removeByIds(Long userId, List<Long> ids) {

        if (userId==null || ids.isEmpty()) {

            return;
        }
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(
                CartItem::getUserId, userId
        ).in(CartItem::getId, ids));
    }

    @Override
    public Sku getSku(Long userId) {
        Sku sku = skuMapper.selectById(userId);

        // sku  对象是否为null
        Optional<Sku> sku1 = Optional.ofNullable(sku);
        if (sku1.isEmpty()) {
            throw new BizException(ResultCode.SKU_NOT_FOUND);
        }


        return sku;
    }
    private CartItem getOwned(Long userId, Long id) {
        CartItem item = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getId, id)
                .eq(CartItem::getUserId, userId));

        // 查不到有两种可能：真没这条，或是别人的。对外统一成"不存在"
        if (item == null) {
            throw new BizException(ResultCode.CART_ITEM_NOT_FOUND);
        }
        return item;
    }
    /**
     * 实体 → VO，顺带补齐商品信息。
     *
     * <p>商品被删除或规格停用时<b>不抛异常</b>，而是返回一条 {@code available=false}
     * 的 VO：购物车里有失效商品是常态，整页报错会让用户连能买的都结不了账。
     */
    private CartItemVO toVO(CartItem row) {
        try {
            Sku sku = skuStockService.getSku(row.getSkuId());
            Product product = productService.getProduct(row.getProductId());

            // 单价取 SKU 现价；SPU 上的 price 只是"最低价"冗余，不能用作结算价
            BigDecimal price = sku.getPrice() == null ? BigDecimal.ZERO : sku.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(row.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            // 可买 = 规格启用有货 + 商品在售 + 库存够本次数量
            boolean available = sku.available() && product.onSale() && sku.hasStock(row.getQuantity());

            return new CartItemVO(row.getId(), row.getProductId(), row.getSkuId(),
                    product.getTitle(), sku.getSpec(), product.getMainImage(),
                    price, row.getQuantity(), row.isChecked(), sku.getStock(), available, subtotal);
        } catch (BizException e) {
            // 规格或商品已经被删：条目留着但标失效，只展示 ID 与数量
            return new CartItemVO(row.getId(), row.getProductId(), row.getSkuId(),
                    "商品已失效", null, null,
                    BigDecimal.ZERO, row.getQuantity(), row.isChecked(), 0, false, BigDecimal.ZERO);
        }
    }

}
