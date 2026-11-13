package com.malls.user.controller;

import com.malls.common.api.Result;
import com.malls.common.ratelimit.RateLimit;
import com.malls.common.security.RequireLogin;
import com.malls.common.security.UserContext;
import com.malls.user.dto.LoginRequest;
import com.malls.user.dto.RefreshTokenRequest;
import com.malls.user.dto.RegisterRequest;
import com.malls.user.service.AuthService;
import com.malls.user.vo.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @PostMapping("/register")
    //同一ip每60最多注册10次
    @RateLimit(key = "auth:register",limit = 10,seconds = 60,byUser = false)
    public Result<LoginResponse> register(@RequestBody RegisterRequest registerRequest){
        LoginResponse register = authService.register(registerRequest);
        return Result.ok(register);

    }
    @PostMapping("/login")
    //同一ip每60最多登录10次
    @RateLimit(key = "auth:login",limit = 10,seconds = 60,byUser = false)
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest,
                                       HttpServletRequest httpServletRequest){
        //获取真实ip
        String ip = clientIp(httpServletRequest);
        LoginResponse response = authService.login(loginRequest,ip);
        return Result.ok(response);
    }

    private String clientIp(HttpServletRequest httpServletRequest) {
        String forwarded = httpServletRequest.getHeader("X-Forwarded-For");
        if (forwarded!=null&&!forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();

        }
        return httpServletRequest.getRemoteAddr();

    }

    @PostMapping("/refresh")
    @RateLimit(key = "auth:refresh",limit = 20,seconds = 60)
    public Result<LoginResponse>refresh(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest
            ){
        LoginResponse refreshResponse = authService.refresh(refreshTokenRequest.refreshToken());
        return Result.ok(refreshResponse);
    }
    @PostMapping
    @RequireLogin
    @RateLimit(key = "auth:logout", limit = 10, seconds = 60)
    public Result<Void> logout(){
        Long lid = UserContext.requireUserId();
        authService.logout(lid);
        return Result.ok();
    }
}
