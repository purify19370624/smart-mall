package com.malls.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.malls.order.domain.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}