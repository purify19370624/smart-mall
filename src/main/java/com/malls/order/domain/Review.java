package com.malls.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("pms_review")
public class Review extends BaseEntity {

    /** 被评价商品，商品详情页按此聚合评价与评分 */
    private Long productId;

    /** 具体规格，可为空（用户只评 SPU 不评 SKU） */
    private Long skuId;

    /** 来源订单 id，保证"只有买过的人能评"，也用于防重复评价 */
    private Long orderId;

    /** 评价人 */
    private Long userId;

    /** 星级 1~5，商品 rating 与 review_count 由它汇总而来 */
    private int rating;

    /** 评价正文，可为空（只打星也算有效评价） */
    private String content;

    /** 图片地址，逗号分隔，与商品图集同约定 */
    private String images;

    /** 是否匿名，前端展示时隐藏昵称与头像 */
    private boolean anonymous;

    /** 商家回复，一条评价只允许回复一次 */
    private String reply;

    /** 归属校验：防止 A 用户删改 B 用户的评价 */
    public boolean ownedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /** 是否已被商家回复，客服后台据此筛选待回复 */
    public boolean replied() {
        return reply != null && !reply.isBlank();
    }

    /** 是否好评（4 星及以上），好评率统计口径 */
    public boolean positive() {
        return rating >= 4;
    }
}