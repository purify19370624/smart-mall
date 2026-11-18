package com.malls.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.malls.catalog.domain.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /**
     * 条件更新扣减库存：只有 stock >= num 才会成功，返回 0 说明库存不足。
     *
     * <p>为什么不用"先查再改"：check-then-act 在并发下会超卖；
     * 这条 UPDATE 由数据库行锁保证原子性，是防超卖的第一道闸门。
     *
     * @param skuId SKU ID
     * @param num   扣减数量
     * @return 影响行数，0 表示库存不足或 SKU 不可售
     */
    int deductStock(@Param("skuId") Long skuId, @Param("num") int num);

    /**
     * 条件更新回补库存：取消订单 / 退款时调用。
     *
     * <p>回补不涉及库存上限，所以只校验"规格还在且仍启用"，返回 0 行说明规格已删或已停用。
     *
     * @param skuId SKU ID
     * @param num   回补数量
     * @return 影响行数，0 表示 SKU 不存在或已停用
     */
    int restoreStock(@Param("skuId") Long skuId, @Param("num") int num);

}
