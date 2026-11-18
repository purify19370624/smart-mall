package com.malls.catalog.controller;

// 库存调整请求 DTO：只有一个 delta 字段
import com.malls.catalog.dto.StockAdjustRequest;
// 库存服务接口：扣减/回补/调整
import com.malls.catalog.service.SkuStockService;
// 规格 VO：把实体转成前端需要的字段
import com.malls.catalog.vo.SkuVO;
// 统一返回结构
import com.malls.common.api.Result;
// 角色校验注解：类上标了它，接口只对指定角色开放
import com.malls.common.security.RequireRole;
// 角色枚举
import com.malls.user.domain.Role;
// 触发请求体字段校验（@NotNull 等）
import jakarta.validation.Valid;
// Lombok 构造器注入
import lombok.RequiredArgsConstructor;
// Spring MVC 注解
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台库存管理接口层。
 *
 * <p><b>接口清单</b>：
 * <table border="1">
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>PUT</td><td>/api/admin/skus/{id}/stock</td><td>增量调整库存（正入库、负出库）</td></tr>
 * </table>
 *
 * <p><b>为什么用增量（{@code delta}）而不是"设置成 N"</b>：
 * 两个运营同时操作同一个 SKU 时，"设为 100"会让后写的覆盖先写的；
 * 而"加 10 件""减 5 件"能正确叠加。绝对值接口在并发下必然丢更新。
 *
 * <p><b>最关键的验收点</b>：{@code delta < 0} 复用 {@code deductStock} 的条件更新
 * （{@code where stock >= ?}），因此"把库存扣穿"必然返回 <b>13001</b>，
 * <b>且库存数字一个都不动</b>——update 的 where 没命中，SQL 压根没执行 set。
 */
@RestController                                  // REST 控制器
@RequestMapping("/api/admin/skus")               // 公共路径前缀：后台 + 规格
@RequireRole({Role.OPERATOR, Role.ADMIN})        // 类级鉴权：运营或管理员，满足其一即可
@RequiredArgsConstructor                         // 构造器注入
public class AdminSkuController {

    // 库存服务：面向接口注入，将来换成 Redis 预扣实现，本类不用改
    private final SkuStockService skuStockService;

    /**
     * 调整库存。
     *
     * <p>请求示例（出库 50 件）：
     * <pre>
     * PUT /api/admin/skus/2120351656475033602/stock
     * Authorization: Bearer eyJhbGciOi...
     * Content-Type: application/json
     *
     * { "delta": -50 }
     *
     * → { "code": 2000, "data": { "id": 212..., "spec": "8GB+128GB 曜石黑", "stock": 450 } }
     * </pre>
     *
     * <p>返回值里的 {@code stock} 是<b>调整后重新查出来的真实库存</b>，
     * 不是"原库存 + delta"算出来的，因为条件更新不回写实体。
     *
     * <p>扣穿示例（库存 450，要出 99999 件）：
     * <pre>
     * → { "code": 13001, "message": "「8GB+128GB 曜石黑」库存不足，当前可用 450 件" }
     * </pre>
     *
     * <p>可能的错误码：4001 delta 未传；4010 未登录；4030 角色不足；
     * 12003 规格不存在；13001 出库数量超过库存。
     *
     * @param id      规格 ID（从 URL 路径取）
     * @param request 只含 delta 的请求体：正数入库、负数出库、0 表示不调整
     * @return 调整后的 SKU（含最新库存）
     */
    @PutMapping("/{id}/stock")                   // 映射 PUT /api/admin/skus/{id}/stock
    public Result<SkuVO> adjustStock(
            // @PathVariable 把 URL 里的 {id} 绑定到方法参数；Long 由 Spring 自动做类型转换
            @PathVariable Long id,
            // @RequestBody 读请求体 JSON 并反序列化；@Valid 触发 StockAdjustRequest 上的 @NotNull
            @Valid @RequestBody StockAdjustRequest request) {

        // 调服务：正数走回补、负数走扣减（扣减是条件更新，不够就抛 13001）
        var sku = skuStockService.adjust(id, request.delta());

        // 实体转 VO：只暴露前端需要的字段，不把整个数据库实体甩出去
        SkuVO vo = SkuVO.of(sku);

        // 包成统一返回结构
        return Result.ok(vo);
    }
}
