package com.malls.user.controller;

// 统一返回结构
import com.malls.common.api.Result;
// 登录校验注解：类上标了，所有地址接口都要求登录
import com.malls.common.security.RequireLogin;
// 登录上下文：取当前用户 ID
import com.malls.common.security.UserContext;
// 地址请求 DTO（6 个字段 + isDefault）
import com.malls.user.dto.AddressRequest;
// 地址服务接口
import com.malls.user.service.AddressService;
// 地址 VO
import com.malls.user.vo.AddressVO;
// 触发请求体字段校验
import jakarta.validation.Valid;
// 构造器注入
import lombok.RequiredArgsConstructor;
// Spring MVC 注解
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 地址列表用 List 承载
import java.util.List;

/**
 * 收货地址接口层。
 *
 * <p><b>接口清单</b>（全部需要登录）：
 * <table border="1">
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>/api/users/me/addresses</td><td>我的地址列表，默认在最前</td></tr>
 *   <tr><td>POST</td><td>/api/users/me/addresses</td><td>新增；第一条自动设为默认</td></tr>
 *   <tr><td>PUT</td><td>/api/users/me/addresses/{id}</td><td>全量修改 6 个字段</td></tr>
 *   <tr><td>DELETE</td><td>/api/users/me/addresses/{id}</td><td>逻辑删除；删默认会自动顶一条</td></tr>
 *   <tr><td>PUT</td><td>/api/users/me/addresses/{id}/default</td><td>设为默认，原默认自动取消</td></tr>
 * </table>
 *
 * <p><b>为什么路径挂在 {@code /api/users/me/addresses}</b>：从 URL 上就表达了
 * "这是当前用户自己的资源"。但要注意——<b>路径只是让意图更清楚，防越权靠的仍是
 * Service 里的双条件查询</b>（{@code where id = ? and user_id = ?}）。
 *
 * <p><b>为什么每个方法都要传 {@code UserContext.requireUserId()}</b>：用户 ID 只能来自令牌。
 * 如果做成"从请求参数取 userId"，前端随便填一个就能操作别人的地址。
 */
@RestController                                  // REST 控制器
@RequestMapping("/api/users/me/addresses")       // 公共前缀：当前用户的地址簿
@RequireLogin                                     // 类级登录校验
@RequiredArgsConstructor                          // 构造器注入
public class AddressController {

    // 地址服务：接口类型，实现是 AddressServiceImpl
    private final AddressService addressService;

    /**
     * 我的地址列表。默认地址在前，其余按新增时间倒序。
     *
     * <p>请求示例：
     * <pre>
     * GET /api/users/me/addresses
     * Authorization: Bearer eyJhbGciOi...
     *
     * → { "code": 2000, "data": [
     *       { "receiver": "张三", "phone": "138****0004", "detail": "武汉商学院",
     *         "isDefault": true } ] }
     * </pre>
     *
     * <p>没有地址时返回空数组而不是 null，前端可直接判断 length。
     *
     * @return 地址列表
     */
    @GetMapping                                       // 映射 GET /api/users/me/addresses
    public Result<List<AddressVO>> list() {
        // 取当前用户 ID：只能来自令牌，防止越权查别人的地址簿
        Long userId = UserContext.requireUserId();

        // 查该用户全部地址，默认地址排最前
        var addresses = addressService.list(userId);

        // 包成统一返回结构
        return Result.ok(addresses);
    }

    /**
     * 新增地址。
     *
     * <p>请求示例：
     * <pre>
     * POST /api/users/me/addresses
     * Authorization: Bearer eyJhbGciOi...
     * Content-Type: application/json
     *
     * { "receiver": "李四", "phone": "13800002222", "province": "湖北省",
     *   "city": "武汉市", "district": "江汉区", "detail": "解放大道 1 号", "isDefault": true }
     * </pre>
     *
     * <p>{@code isDefault} 可以不传：<b>用户的第一条地址会被强制设为默认</b>，
     * 之后新增的默认不是默认（要显式传 true）。这保证"有地址就一定有默认地址"。
     *
     * <p>可能的错误码：4001 手机号格式不对/收货人为空；4010 未登录。
     *
     * @param request 地址内容
     * @return 新增的地址（含生成的 ID）
     */
    @PostMapping                                      // 映射 POST /api/users/me/addresses
    public Result<AddressVO> create(
            // 请求体：6 个地址字段 + 可选的 isDefault；@Valid 校验必填与格式
            @Valid @RequestBody AddressRequest request) {

        // 当前用户 ID：新地址的 user_id 由服务端决定，前端传什么都不认
        Long userId = UserContext.requireUserId();

        // 调服务新增：判断是否第一条 → 需要默认则先清别人默认位 → 插入
        var address = addressService.create(userId, request);

        // 包成统一返回结构
        return Result.ok(address);
    }

    /**
     * 修改地址。
     *
     * <p>语义是"全量覆盖 6 个字段"，不是局部更新：前端要把完整表单都提交上来，
     * 没传的字段会被当成空值。这和 {@code PUT /api/users/me} 的局部更新语义不同。
     *
     * <p>请求示例：
     * <pre>
     * PUT /api/users/me/addresses/2120351656407924999
     * Authorization: Bearer eyJhbGciOi...
     * Content-Type: application/json
     *
     * { "receiver": "李四", "phone": "13800002222", "province": "湖北省",
     *   "city": "武汉市", "district": "武昌区", "detail": "中山路 8 号", "isDefault": false }
     * </pre>
     *
     * <p>{@code isDefault} 传 false <b>不会取消</b>当前的默认状态——要换默认得调
     * {@code PUT .../{id}/default}，避免"改个门牌号结果默认地址没了"。
     *
     * <p>可能的错误码：11006 地址不存在或不属于当前用户（两种情况同一个码）；
     * 4001 参数校验失败。
     *
     * @param id      地址 ID（路径参数）
     * @param request 新的地址内容
     * @return 修改后的地址
     */
    @PutMapping("/{id}")                               // 映射 PUT /api/users/me/addresses/{id}
    public Result<AddressVO> update(
            // 路径变量 {id}，Spring 自动转 Long
            @PathVariable Long id,
            // 新的地址内容，全量覆盖
            @Valid @RequestBody AddressRequest request) {

        // 当前用户 ID：服务层会用它做 id + user_id 双条件校验
        Long userId = UserContext.requireUserId();

        // 调服务修改：先查归属，再决定是否动默认位，最后 update
        var address = addressService.update(userId, id, request);

        // 包成统一返回结构
        return Result.ok(address);
    }

    /**
     * 删除地址（逻辑删除，库里 {@code deleted} 置 1）。
     *
     * <p>如果删掉的正好是默认地址，系统会把剩下<b>最新的一条</b>自动顶为默认，
     * 保证"有地址就有默认"这个不变式不被破坏。
     *
     * <p>请求示例：
     * <pre>
     * DELETE /api/users/me/addresses/2120351656407924999
     * → { "code": 2000, "message": "成功", "data": null }
     * </pre>
     *
     * <p>为什么用逻辑删除而不是物理 delete：历史订单要能还原"当时寄到哪"，
     * 地址被硬删后，订单详情里的收货信息就成了孤儿数据。
     *
     * <p>可能的错误码：11006 地址不存在或不属于当前用户。
     *
     * @param id 地址 ID
     * @return 无数据的成功结果
     */
    @DeleteMapping("/{id}")                            // 映射 DELETE /api/users/me/addresses/{id}
    public Result<Void> delete(
            // 要删除的地址 ID
            @PathVariable Long id) {

        // 当前用户 ID：服务层校验归属，删不到别人的地址
        Long userId = UserContext.requireUserId();

        // 调服务删除：逻辑删除 → 若删的是默认地址，把剩下最新的一条顶上
        addressService.delete(userId, id);

        // 无数据返回
        return Result.ok();
    }

    /**
     * 设为默认地址。
     *
     * <p>两步在同一个事务里：先把该用户其他地址的 {@code is_default} 置 0，
     * 再把目标地址置 1。并发最坏是有两条默认（用户能自己修正），
     * 但绝不会出现"没有默认地址"——后者会直接阻断下单。
     *
     * <p>请求示例：
     * <pre>
     * PUT /api/users/me/addresses/2120351656407924999/default
     * → { "code": 2000, "data": { ...该地址, "isDefault": true } }
     * </pre>
     *
     * <p>可能的错误码：11006 地址不存在或不属于当前用户。
     *
     * @param id 地址 ID
     * @return 设为默认后的地址
     */
    @PutMapping("/{id}/default")                       // 映射 PUT /api/users/me/addresses/{id}/default
    public Result<AddressVO> setDefault(
            // 要设为默认的地址 ID
            @PathVariable Long id) {

        // 当前用户 ID：服务层用它限定「只能清自己名下的默认位」
        Long userId = UserContext.requireUserId();

        // 调服务：清其他地址默认位 → 把目标地址置 1
        var address = addressService.setDefault(userId, id);

        // 包成统一返回结构
        return Result.ok(address);
    }
}
