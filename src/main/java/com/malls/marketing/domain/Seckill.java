package com.malls.marketing.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("mkt_seckill")
public class Seckill extends BaseEntity {


    /** 商品 SPU ID，便于列表页一次性带出商品信息。 */
    private Long productId;

    /** 参与秒杀的 SKU ID，秒杀按 SKU 维度限库存。 */
    private Long skuId;

    /** 秒杀价，明显低于 SKU 原价，下单时作为成交价快照。 */
    private BigDecimal seckillPrice;

    /** 活动库存总量。 */
    private int stock;

    /** 已售数量，与 stock 配合判断是否售罄。 */
    private int sold;

    /** 活动开始时间。 */
    private LocalDateTime startTime;

    /** 活动结束时间。 */
    private LocalDateTime endTime;

    /** 活动状态：未开始 / 进行中 / 已结束。 */
    private SeckillStatus status;

    /** 当前是否处于活动窗口：状态进行中且系统时间落在起止时间之间。 */
    public boolean running() {
        LocalDateTime now = LocalDateTime.now();
        return status == SeckillStatus.ACTIVE
                && startTime != null && endTime != null
                && !now.isBefore(startTime) && !now.isAfter(endTime);
    }

    /** 剩余名额，售罄返回 0。 */
    public int remaining() {
        return Math.max(stock - sold, 0);
    }

    /** 是否已售罄。 */
    public boolean soldOut() {
        return remaining() <= 0;
    }

}