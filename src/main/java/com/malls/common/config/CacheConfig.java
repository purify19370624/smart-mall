package com.malls.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 本地缓存配置（Caffeine）。
 *
 * <p>缓存三问（方案文档 19.2 节）：
 * <ol>
 *   <li><b>缓存什么</b>：分类树、商品详情 VO。VO 已把品牌名、面包屑、SKU 列表拼装完，
 *       命中缓存就是一次内存读取，不再查库</li>
 *   <li><b>何时失效</b>：商品增/改/上下架/调库存统一 {@code @CacheEvict(allEntries = true)}，
 *       写操作"宁可多清"</li>
 *   <li><b>穿透怎么办</b>：单体阶段接受"查不到就查库"，生产可加空值缓存或布隆过滤器</li>
 * </ol>
 *
 * <p>为什么用 Caffeine 而不是自己 new 一个 HashMap：它自带容量上限与过期淘汰，
 * {@code maximumSize} 保证不会被热点 key 打爆内存，{@code expireAfterWrite} 保证
 * 数据不会一直陈旧。
 *
 * <p>演进预告：Caffeine 是<b>进程内</b>缓存，Day08 多实例部署时各实例缓存不一致。
 * Day07 引入 Redis 后换集中式缓存，"旁路缓存 + 写后失效"的模式原样照搬，只换 CacheManager。
 */
@Configuration
public class CacheConfig {

    /** 分类树缓存名 */
    public static final String CATEGORY_TREE = "category:tree";
    /** 商品详情缓存名 */
    public static final String PRODUCT_DETAIL = "product:detail";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofMinutes(10)));
        return manager;
    }
}
