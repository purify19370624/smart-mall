package com.malls.user.service;

import com.malls.user.dto.LoginRequest;
import com.malls.user.dto.RegisterRequest;
import com.malls.user.vo.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

public interface AuthService {
    LoginResponse register(RegisterRequest registerRequest);

    LoginResponse login(LoginRequest loginRequest, String ip);

    LoginResponse refresh(@NotBlank(message = "刷新令牌不能为空") String s);

    void logout(Long lid);
}
