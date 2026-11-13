package com.malls.user.controller;

import com.malls.common.api.Result;
import com.malls.common.security.UserContext;
import com.malls.user.service.UserService;
import com.malls.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    @GetMapping("/me")
    public Result<UserVO> me(){
        Long l = UserContext.requireUserId();
        UserVO me = userService.me(l);
        return Result.ok(me);
    }
}
