package com.malls.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化约定：把 {@code Long} 主键序列化成字符串。
 *
 * <p>为什么必须这么做：本项目主键是雪花 ID（如 {@code 1000000000000000101}），
 * 已经超过 JS 的 {@code Number.MAX_SAFE_INTEGER}（9007199254740991）。
 * 如果按数字下发，前端 {@code JSON.parse} 会悄悄把它变成
 * {@code 1000000000000000100}，再回传给后端就是「地址不存在 / 商品不存在」，
 * 而且<b>不报错、只是数据对不上</b>，排查成本极高。
 *
 * <p>为什么只处理包装类型 {@code Long}、不处理基本类型 {@code long}：
 * 分页的 {@code total/pageNum/pages} 是基本类型，前端分页组件需要数字，
 * 它们不可能超过安全整数（条数不会到 1e15），保持数字更省事。
 *
 * <p>反序列化不用管：Jackson 默认就能把 {@code "123"} 转成 {@code Long}。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}
