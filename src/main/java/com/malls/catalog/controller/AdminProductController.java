package com.malls.catalog.controller;

import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.dto.ProductQuery;
import com.malls.catalog.service.AdminProductService;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.ProductVO;
import com.malls.common.api.PageResult;
import com.malls.common.api.Result;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import com.malls.common.security.RequireRole;
import com.malls.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 后台商品管理接口（运营 / 管理员）。
 *
 * <p>类级 {@link RequireRole} 把整个后台商品入口锁住：方法级优先、类级兜底，
 * 以后新增的后台方法默认也受保护，不会因为忘了加注解而裸奔。
 *
 * <p>为什么单独开 {@code /api/admin/**} 前缀而不是复用 {@code /api/products}：
 * 前缀本身就是一道权限边界（网关/日志/限流都能按前缀统一处理），
 * 也避免"同一个 URL 靠角色返回不同数据"这种难以排查的隐式行为。
 */
@RestController
@RequestMapping("/api/admin/products")
@RequireRole({Role.OPERATOR, Role.ADMIN})
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    /**
     * 后台列表：不加"仅上架"过滤，运营能看到下架商品。
     *
     * <p>参数与前台列表保持一致，额外多一个可选 {@code status} 精确过滤。
     */
    @GetMapping
    public Result<PageResult<ProductVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {

        ProductQuery query = new ProductQuery(keyword, categoryId, brandId,
                minPrice, maxPrice, sort, pageNum, pageSize);
        return Result.ok(adminProductService.page(query, parseStatus(status)));
    }

    /** 后台详情：走 detail 的取数逻辑，但绕开缓存（运营要看最新数据） */
    @GetMapping("/{id}")
    public Result<ProductDetailVO> detail(@PathVariable Long id) {
        return Result.ok(adminProductService.detail(id));
    }

    /** 上架 */
    @PutMapping("/{id}/on-shelf")
    public Result<ProductVO> onShelf(@PathVariable Long id) {
        return Result.ok(adminProductService.onShelf(id));
    }

    /** 下架 */
    @PutMapping("/{id}/off-shelf")
    public Result<ProductVO> offShelf(@PathVariable Long id) {
        return Result.ok(adminProductService.offShelf(id));
    }

    /** 空串视为"不过滤"；非法值给一句能直接看懂的话，而不是 500 */
    private ProductStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ProductStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 只能是 ON_SALE 或 OFF_SHELF");
        }
    }
}
