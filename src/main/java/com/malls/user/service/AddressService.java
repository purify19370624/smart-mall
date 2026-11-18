package com.malls.user.service;

import com.malls.user.domain.UserAddress;
import com.malls.user.dto.AddressRequest;
import com.malls.user.vo.AddressVO;

import java.util.List;

/**
 * 收货地址服务接口。
 *
 * <p>两条铁律：
 * <ol>
 *   <li><b>默认地址唯一</b>：设新默认前先清掉该用户其它地址的默认位，两步同一事务</li>
 *   <li><b>单条操作必须双条件</b>（{@code id AND user_id}）：只按 id 查会越权</li>
 * </ol>
 *
 * <p>所有方法都要求显式传入 {@code userId}，绝不从请求参数里取——
 * 用户 ID 只能来自 {@code UserContext}（即令牌）。
 *
 * <p>实现类见 {@link com.malls.user.service.impl.AddressServiceImpl}。
 */
public interface AddressService {

    /**
     * 查询我的地址列表，默认地址排最前，其次按创建时间倒序。
     *
     * @param userId 当前用户 ID
     * @return 地址列表；没有地址时返回空列表
     */
    // 列表：默认在前，其余按 ID 倒序（雪花 ID 单调递增）
    List<AddressVO> list(Long userId);

    /**
     * 新增地址。用户的第一条地址自动设为默认。
     *
     * <p>不加这条规则，用户下的第一单会因为"没有默认地址"卡住。
     *
     * @param userId  当前用户 ID
     * @param request 地址内容
     * @return 新建的地址
     */
    // 新增：判断是否第一条 → 需要默认则先清别人 → 插入
    AddressVO create(Long userId, AddressRequest request);

    /**
     * 修改地址（全量覆盖 6 个字段）。
     *
     * @param userId    当前用户 ID
     * @param addressId 地址 ID
     * @param request   新内容
     * @return 修改后的地址
     * @throws com.malls.common.exception.BizException 地址不存在或不属于当前用户时统一 11006
     */
    // 修改：先做归属校验，再按需动默认位
    AddressVO update(Long userId, Long addressId, AddressRequest request);

    /**
     * 删除地址（逻辑删除）。删掉的是默认地址时，剩下最新的一条自动顶上。
     *
     * @param userId    当前用户 ID
     * @param addressId 地址 ID
     * @throws com.malls.common.exception.BizException 地址不存在或不属于当前用户时 11006
     */
    // 删除：逻辑删除 → 若删的是默认，把剩下最新的一条设为默认
    void delete(Long userId, Long addressId);

    /**
     * 把指定地址设为默认，同时清掉其它地址的默认位。
     *
     * @param userId    当前用户 ID
     * @param addressId 地址 ID
     * @return 设为默认后的地址
     * @throws com.malls.common.exception.BizException 地址不存在或不属于当前用户时 11006
     */
    // 设默认：清其他 → 置自己为 1，同一事务
    AddressVO setDefault(Long userId, Long addressId);

    /**
     * 按 ID 取当前用户名下的地址实体。
     *
     * <p>给下单用：下单要把收货信息<b>拷贝成快照</b>存进订单，需要完整字段，
     * 而不是给前端看的 VO。走 Service 接口而不是直接查 {@code user_address} 表，
     * 是为了守住"一张表只有一个写主、跨包不碰对方 mapper"的纪律。
     *
     * @param userId    当前用户 ID
     * @param addressId 地址 ID
     * @return 地址实体，永不为 {@code null}
     * @throws com.malls.common.exception.BizException 地址不存在或不属于当前用户时统一 11006
     */
    // 双条件单查：查不到即"不存在或不是你的"，不泄露存在性
    UserAddress getOwned(Long userId, Long addressId);
}
