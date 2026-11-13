package com.malls.user.service.impl;

import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import com.malls.user.domain.UserAccount;
import com.malls.user.mapper.UserAccountMapper;
import com.malls.user.service.UserService;
import com.malls.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAccountMapper userAccountMapper;
    @Override
    public UserVO me(Long UserId) {
        UserAccount user = getUser(UserId);

        return UserVO.of(user);
    }

    @Override
    public UserAccount getUser(Long UserId) {
        UserAccount userAccount = userAccountMapper.selectById(UserId);
        if(userAccount==null){
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return userAccount;
    }
}
