package com.malls.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import com.malls.common.security.JwtUtil;
import com.malls.user.domain.AccountStatus;
import com.malls.user.domain.Role;
import com.malls.user.domain.UserAccount;
import com.malls.user.dto.LoginRequest;
import com.malls.user.dto.RegisterRequest;
import com.malls.user.mapper.UserAccountMapper;
import com.malls.user.service.AuthService;
import com.malls.user.service.MembershipPolicy;
import com.malls.user.vo.LoginResponse;
import com.malls.user.vo.UserVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserAccountMapper userAccountMapper;
    private final MembershipPolicy membershipPolicy;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private  static  final String  PHONE_REGEX = "^1[3-9]\\d{9}$";
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest registerRequest) {
        String username = registerRequest.username().trim();
        String phone = registerRequest.phone().trim();
        long userCount = userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery().
                eq(UserAccount::getUsername, username));
        long phoneCount = userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery().
                eq(UserAccount::getPhone, phone));
        if (userCount>0||phoneCount>0) {
            throw new BizException(ResultCode.USERNAME_EXISTS,"账户已经被注册！");
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPhone(phone);
        //密码加密
        userAccount.setPassword(passwordEncoder.encode(registerRequest.password()));
        userAccount.setRole(Role.CUSTOMER);
        userAccount.setStatus(AccountStatus.ENABLED);
        userAccount.setLevel(membershipPolicy.leveOf(BigDecimal.ZERO));
        userAccount.setPoints(0);
        userAccount.setTotalConsumption(BigDecimal.ZERO);
        userAccount.setTokenVersion(0);
        try {
            userAccountMapper.insert(userAccount);
        } catch (Exception e) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        log.info("新用户注册成功！");

        //签发令牌

        return issueTokens(userAccount);
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest, String ip) {
        String account = loginRequest.account().trim();
        boolean matches = account.matches(PHONE_REGEX);
        UserAccount userAccount = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(matches ? UserAccount::getPhone : UserAccount::getUsername, account));
        if(userAccount==null){
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        if(!passwordEncoder.matches(loginRequest.password(),userAccount.getPassword())){
            throw  new BizException(ResultCode.USERNAME_EXISTS);
        }
        userAccount.recordLogin(ip);
        log.info("登录成功");

        return issueTokens(userAccount);
    }

    @Override
    public LoginResponse refresh(String token) {
        Optional<Claims> parse = jwtUtil.parse(token);
        if(parse.isEmpty()){
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        Claims claims = parse.get();
        if(!JwtUtil.TYPE_REFRESH.equals(token)){
            throw new BizException(ResultCode.TOKEN_INVALID, "请使用刷新令牌续期");
        }
        Long userId = userIdOf(claims);
        UserAccount userAccount = userAccountMapper.selectById(userId);
        if(userAccount==null||!userAccount.enabled()){
            throw  new BizException(ResultCode.TOKEN_INVALID,"登录状态已失效，请重新登录");
        }
        return issueTokens(userAccount);
    }

    @Override
    public void logout(Long lid) {
       int rows =  userAccountMapper.bumpTokenVersion(lid);
       log.info("用户登出 id={} 版本号自增行数={}", lid, rows);
    }

    private Long userIdOf(Claims claims) {
        String subject = claims.getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
    }

    private LoginResponse issueTokens(UserAccount userAccount){
        //2h令牌
        String accessToken = jwtUtil.createAccessToken(userAccount.getId(), userAccount.getUsername(), userAccount.getRole(), userAccount.getTokenVersion());
        //7days令牌
        String refreshToken = jwtUtil.createRefreshToken(userAccount.getId(), userAccount.getUsername(), userAccount.getRole(), userAccount.getTokenVersion());
        return LoginResponse.of(accessToken,refreshToken,
                jwtUtil.accessTtlSeconds(), UserVO.of(userAccount));
    }
}
