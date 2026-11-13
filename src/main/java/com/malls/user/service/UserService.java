package com.malls.user.service;

import com.malls.user.domain.UserAccount;
import com.malls.user.vo.UserVO;
import org.springframework.stereotype.Service;


public interface UserService {
    UserVO me(Long UserId);
    UserAccount getUser(Long UserId);
}
