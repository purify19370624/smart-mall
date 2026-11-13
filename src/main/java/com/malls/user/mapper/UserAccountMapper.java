package com.malls.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.malls.user.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    int bumpTokenVersion(@Param("id") Long lid);

}
