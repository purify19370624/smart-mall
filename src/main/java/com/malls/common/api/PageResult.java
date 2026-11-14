package com.malls.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

//   mybatis 数据库 分页工具类

public record PageResult<T>(List<T> records, long total, long pageNum, long pageSize, long pages) {

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(),
                page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 实体分页 → VO 分页，避免把数据库实体直接暴露给前端。
     *
     * <p>实体里有 password 这类字段，一旦直接返回就是事故；VO 是唯一对外契约。
     */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> mapper) {
        return new PageResult<>(page.getRecords().stream().map(mapper).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 空页（查不到数据时用，前端不用判空） */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(List.of(), 0L, pageNum, pageSize, 0L);
    }

    /** 总页数 */
    public long pages() {
        return pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
    }
}
