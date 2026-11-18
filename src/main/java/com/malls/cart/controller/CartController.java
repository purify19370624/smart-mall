package com.malls.cart.controller;



import com.malls.cart.dto.AddCartItemRequest;
import com.malls.cart.dto.CheckAllRequest;
import com.malls.cart.service.CartService;
import com.malls.cart.vo.CartItemVO;
import com.malls.cart.vo.CartVO;
import com.malls.common.api.Result;
import com.malls.common.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor  // 构造器注入  静态方法注入
public class CartController {


    // 引入服务
    private final CartService cartService;




    //我的购物车
    @GetMapping
    public Result<CartVO> list(){

        // 当前用户 ID
        Long userId = UserContext.requireUserId();

        // 查看购物车+ 汇总金额
       return Result.ok(cartService.CartListItem(userId));
    }



    @PostMapping("/items")
    public Result<CartItemVO> addItem(@Valid  @RequestBody AddCartItemRequest request){

        Long userId = UserContext.requireUserId();
        return  Result.ok(cartService.addCartItem(userId,request));

    }


    /**
     * 删除单条。
     *
     * <p>可能的错误码：13004 条目不存在或不属于当前用户。
     *
     * @param id 条目 ID
     * @return 无数据的成功结果
     */
    @DeleteMapping("/items/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();

        // 删除单个
        cartService.removeCartItem(userId, id);

        return Result.ok();
    }

    /**
     * 清空购物车。
     *
     * @return 无数据的成功结果
     */
    @DeleteMapping
    public Result<Void> clear() {
        // 获取id
        Long userId = UserContext.requireUserId();

        // 清空
        cartService.clear(userId);

        return Result.ok();
    }
    /**
     * 全选 / 取消全选。
     *
     * <p>请求示例：
     * <pre>
     * PUT /api/cart/checked
     * { "checked": true }
     * </pre>
     *
     * @param request 勾选状态
     * @return 无数据的成功结果
     */
    @PutMapping("/checked")
    public Result<Void> checkAll(@Valid @RequestBody CheckAllRequest request) {
        Long userId = UserContext.requireUserId();

        cartService.checkAll(userId, request.checked());

        return Result.ok();
    }
}
