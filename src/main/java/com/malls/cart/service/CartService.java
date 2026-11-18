package com.malls.cart.service;



import com.malls.cart.dto.AddCartItemRequest;
import com.malls.cart.vo.CartItemVO;
import com.malls.cart.vo.CartVO;
import com.malls.catalog.domain.Sku;
import com.malls.common.exception.BizException;
import com.malls.order.domain.CartItem;

import java.util.List;

//写功能的方法
public interface CartService {


    /**
     * 1 加入购物车。同一个 SKU 重复加购会累加数量，而不是插一条新的。
     *
     * @param userId 当前用户 ID
     * @param request 加购请求
     * @return 加购后的条目（含实时价格与库存）
     * @throws BizException 规格不存在 12003；已停用或售罄 12002；数量超库存 13001
     */
    CartItemVO addCartItem(Long userId, AddCartItemRequest request);


    /*
    *  全选 / 取消全选。
    *
    * */

    void checkAll(Long userId, boolean checked);


    /**
     * 取<b>已勾选</b>的条目，供下单使用。
     *
     * <p>这是 order 包唯一的入口：order 不碰 {@code CartItemMapper}，
     * 只通过这个 Service 方法拿数据，Day08 拆服务时改成一次 RPC 即可。
     *
     * @param userId 当前用户 ID
     * @return 勾选的条目；空车或全未勾选时返回空列表
     */
    List<CartItem> listChecked(Long userId);

    /**
     * 2  我的购物车，新加的排前面，并带结算汇总。
     *
     * @param userId 当前用户 ID
     * @return 购物车视图；空车时 items 为空列表而不是 null
     */
    CartVO CartListItem(Long userId);

    /**
     * 3 删除单个购物车的商品
     *
     * @param userId 当前用户 ID
     * @param id     购物车条目 ID
     * @throws BizException 条目不存在或不属于当前用户 13007
     */
     void removeCartItem(Long userId, Long id);

    /**
     * 清空我的购物车。
     *
     * @param userId 当前用户 ID
     */
    void clear(Long userId);

    /**
     * 按 ID 批量删除（下单成功后清理已结算的条目）。
     *
     * <p>带上 {@code userId} 而不是只按 ID 删：即便调用方传错 ID，
     * 也删不掉别人的购物车。
     *
     * @param userId 当前用户 ID
     * @param ids    要删的条目 ID
     */

    void removeByIds(Long userId, List<Long> ids);



    /** 查询规格，找不到即 12003 */
    Sku getSku(Long userId);

}
