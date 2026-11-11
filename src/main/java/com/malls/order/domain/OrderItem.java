package com.malls.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@TableName("oms_order_item")
public class OrderItem extends BaseEntity {
    /** 所属订单 id，按订单查明细走 idx_item_order */
    private Long orderId;

    /** 所属订单号，冗余一份便于按单号直接查（运营导出常用） */
    private String orderNo;

    /** 商品 SPU，售后/评价回写商品维度统计时用 */
    private Long productId;

    /** 商品 SKU，唯一标识"哪一款" */
    private Long skuId;

    /** 快照：下单时的商品标题 */
    private String productTitle;

    /** 快照：下单时的规格描述，如 8GB+128GB 曜石黑 */
    private String skuSpec;

    /** 快照：下单时的商品主图，订单列表直接渲染 */
    private String productImage;

    /** 快照：下单时单价，绝不能再回查 SKU 现价 */
    private BigDecimal price;

    /** 购买数量 */
    private int quantity;

    /** 小计 = price * quantity，落库存储避免每次展示重算（也便于分摊优惠） */
    private BigDecimal totalAmount;

}