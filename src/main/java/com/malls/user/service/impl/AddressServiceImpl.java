package com.malls.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import com.malls.user.domain.UserAddress;
import com.malls.user.dto.AddressRequest;
import com.malls.user.mapper.UserAddressMapper;
import com.malls.user.service.AddressService;
import com.malls.user.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务实现。
 *
 * <p>两条铁律：
 * <ol>
 *   <li><b>默认地址唯一</b>：设新默认前先清掉该用户其他地址的默认位，两步在同一事务里</li>
 *   <li><b>所有单条操作都走 {@code getOwned(userId, id)} 双条件</b>：
 *       只按 id 查会让用户 A 改到用户 B 的地址（越权）</li>
 * </ol>
 *
 * <p>为什么"唯一默认"靠应用层而不是数据库唯一索引：MySQL 唯一索引允许多个 NULL，
 * 而 {@code is_default} 是 0/1 整型；真要约束得建生成列 + 唯一索引或用触发器，
 * 复杂度高、可移植性差。这个场景并发极低（用户点自己家地址），事务 + 先清后设足够。
 */
@Slf4j                     // 生成 log
@Service                   // 注册为业务组件
@RequiredArgsConstructor   // 构造器注入
public class AddressServiceImpl implements AddressService {

    // 地址表 Mapper：单表 CRUD 全走它
    private final UserAddressMapper userAddressMapper;

    /**
     * 我的地址列表，默认地址排最前。
     *
     * <p>排序 {@code is_default DESC, id DESC}：默认地址顶到第一条，其余按新增倒序。
     * 不用 create_time 排序，是因为雪花 ID 单调递增，而时间字段在批量插入时可能重复。
     */
    @Override
    public List<AddressVO> list(Long userId) {
        // 拼查询条件：只查当前用户 + 默认优先 + 新地址优先
        var query = Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)          // 只查自己的地址（防越权第一道）
                .orderByDesc(UserAddress::getIsDefault)      // 默认地址排最前
                .orderByDesc(UserAddress::getId);            // 同优先级时新地址在前

        // 执行查询：MyBatis-Plus 自动追加 deleted = 0
        List<UserAddress> rows = userAddressMapper.selectList(query);

        // 实体列表转 VO 列表：stream + map 保持顺序，toList() 返回不可变列表
        return rows.stream().map(AddressVO::of).toList();
    }

    /**
     * 新增地址；用户的第一条地址自动设为默认。
     *
     * <p>"第一条自动默认"很关键：不加的话用户下的第一单会因为找不到默认地址而卡住。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)     // 查计数 + 清默认 + 插入必须在一个事务里
    public AddressVO create(Long userId, AddressRequest request) {
        // 统计该用户已有多少地址：结果为 0 说明这是第一条
        long count = userAddressMapper.selectCount(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId));

        // 是否作为默认：请求显式要求 true，或者是第一条地址，两者满足其一
        boolean asDefault = Boolean.TRUE.equals(request.isDefault()) || count == 0;

        // 要设为默认时，先把该用户其他地址的默认位清掉（保证唯一）
        if (asDefault) {
            clearDefault(userId, null);
        }

        // 创建实体并逐个赋值（不用 BeanUtils 整体拷贝，避免不可控字段被写进来）
        UserAddress address = new UserAddress();

        // 归属：user_id 只来自令牌解析结果，前端无法指定
        address.setUserId(userId);

        // 6 个业务字段统一 trim 后写入
        fill(address, request);

        // is_default 在库里是 tinyint，这里手动转 0/1
        address.setIsDefault(asDefault ? 1 : 0);

        // 插入：雪花 ID 在 insert 前由 MyBatis-Plus 生成并回填到 entity
        userAddressMapper.insert(address);

        // 打日志：便于排查"地址是谁什么时候加的"
        log.info("新增收货地址 userId={} addressId={} default={}", userId, address.getId(), asDefault);

        // 转 VO 返回（此时 entity 已有 id，前端能拿到新地址主键）
        return AddressVO.of(address);
    }

    /**
     * 修改地址：归属校验通过才允许动。
     *
     * <p>{@code isDefault} 只有显式传 true 才动默认位；传 false 或 null <b>不取消</b>——
     * 否则用户改一下门牌号，默认地址就没了。取消默认只能通过"设别的为默认"实现。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)     // 清默认 + 更新要原子
    public AddressVO update(Long userId, Long addressId, AddressRequest request) {
        // 双条件查询：id + user_id，查不到即抛 11006（越权和不存在同一个码）
        UserAddress address = getOwned(userId, addressId);

        // 只有明确要求设为默认时才处理默认位
        if (Boolean.TRUE.equals(request.isDefault())) {
            // 先清掉自己的默认位之外的其他默认（exceptId 传自己，避免刚清完又置回）
            clearDefault(userId, addressId);

            // 把自己标记为默认
            address.setIsDefault(1);
        }

        // 覆盖 6 个业务字段（全量语义：前端要提交完整表单）
        fill(address, request);

        // 按主键更新整行（非 null 字段才会进 set 子句）
        userAddressMapper.updateById(address);

        // 返回更新后的地址
        return AddressVO.of(address);
    }

    /**
     * 删除（逻辑删除）；删掉的是默认地址时，把剩下最新的一条顶上。
     *
     * <p>顺序很重要：先删再查（查的是删完的列表），否则会把刚删掉的那条又选出来。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)     // 删除 + 补默认必须原子，否则可能留下"无默认"
    public void delete(Long userId, Long addressId) {
        // 先做归属校验并拿到实体（后面要判断它是不是默认地址）
        UserAddress address = getOwned(userId, addressId);

        // 逻辑删除：实际执行 update user_address set deleted = 1 where id = ? and deleted = 0
        userAddressMapper.deleteById(address.getId());

        // 记录日志，便于事后追溯
        log.info("删除收货地址 userId={} addressId={}", userId, addressId);

        // 只有当被删的正好是默认地址时，才需要补一条默认
        if (address.isDefaultAddress()) {
            // 查剩下最新的地址：这里已经排除刚删的那条（deleted = 1）
            var remainQuery = Wrappers.<UserAddress>lambdaQuery()
                    .eq(UserAddress::getUserId, userId)      // 只看自己的
                    .orderByDesc(UserAddress::getId);        // 最新的在前

            // 取第一条（最新的一条）作为新的默认地址
            userAddressMapper.selectList(remainQuery).stream().findFirst()
                    .ifPresent(next -> {
                        // 标记为默认
                        next.setIsDefault(1);
                        // 更新该条记录
                        userAddressMapper.updateById(next);
                    });
        }
    }

    /** 设为默认地址：先清别人的默认位，再把自己设为 1，同一事务内完成 */
    @Override
    @Transactional(rollbackFor = Exception.class)     // 两步必须原子，否则可能短暂出现两条默认
    public AddressVO setDefault(Long userId, Long addressId) {
        // 归属校验：不能把别人的地址设为默认
        UserAddress address = getOwned(userId, addressId);

        // 清掉该用户其他地址的默认位（跳过自己）
        clearDefault(userId, addressId);

        // 把自己置为默认
        address.setIsDefault(1);

        // 更新这一行
        userAddressMapper.updateById(address);

        // 返回更新后的地址
        return AddressVO.of(address);
    }

    /**
     * 双条件查询：既校验归属，又顺带拿到实体，查不到即 11006。
     *
     * <p>为什么"不存在"和"不属于你"返回同一个错误码：如果分开，
     * 攻击者能通过错误码差异探测某个地址 ID 是否真实存在。统一 11006，不给额外信息。
     *
     * <p>开放到接口上（{@code public}）是给下单用的：订单服务要拿完整字段做快照。
     */
    @Override
    public UserAddress getOwned(Long userId, Long addressId) {
        // 同时限定 id 与 user_id：少任何一个条件就是越权漏洞
        UserAddress address = userAddressMapper.selectOne(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getId, addressId)           // 目标地址
                .eq(UserAddress::getUserId, userId));        // 且必须属于当前用户

        // 查不到：可能不存在，也可能不属于你，一律 11006
        if (address == null) {
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND);
        }

        // 返回实体，调用方可直接用
        return address;
    }

    /**
     * 清掉该用户的默认位，exceptId 用于修改时跳过自己。
     *
     * <p>用 {@code update(null, wrapper)} 做条件更新（一条 SQL 清全部），
     * 而不是"查出来循环 updateById"：后者在地址多时是 N 条 SQL。
     */
    private void clearDefault(Long userId, Long exceptId) {
        // 构造条件更新：只改 is_default，限定当前用户
        var update = Wrappers.<UserAddress>lambdaUpdate()
                .eq(UserAddress::getUserId, userId)          // 只清自己的
                .set(UserAddress::getIsDefault, 0);          // 全部置 0

        // exceptId 非空时排除这一条（修改/设默认时不能把自己清掉）
        if (exceptId != null) {
            update.ne(UserAddress::getId, exceptId);
        }

        // 第一个参数传 null 表示"实体无关，只用 wrapper 里的 set 子句"
        userAddressMapper.update(null, update);
    }

    /** 6 个业务字段统一 trim：粘贴来的地址常带首尾空格，会导致打印/导出排版错乱 */
    private void fill(UserAddress address, AddressRequest request) {
        // 收货人
        address.setReceiver(request.receiver().trim());
        // 联系电话
        address.setPhone(request.phone().trim());
        // 省
        address.setProvince(request.province().trim());
        // 市
        address.setCity(request.city().trim());
        // 区/县
        address.setDistrict(request.district().trim());
        // 详细地址（街道门牌）
        address.setDetail(request.detail().trim());
    }
}
