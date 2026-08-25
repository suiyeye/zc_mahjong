package com.zc.zcapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zc.zcapi.common.CurrentUser;
import com.zc.zcapi.dto.LoginResponse;
import com.zc.zcapi.dto.WechatLoginRequest;
import com.zc.zcapi.dto.WechatSessionResponse;
import com.zc.zcapi.entity.AuthTokenEntity;
import com.zc.zcapi.mapper.AuthTokenMapper;
import com.zc.zcapi.common.exception.BusinessException;
import com.zc.zcapi.entity.UserEntity;
import com.zc.zcapi.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final AuthTokenMapper authTokenMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String appId;
    private final String appSecret;
    private final int tokenValidDays;

    public AuthService(AuthTokenMapper authTokenMapper, UserService userService,
                       ObjectMapper objectMapper,
                       @Value("${wechat.app-id:}") String appId,
                       @Value("${wechat.app-secret:}") String appSecret,
                       @Value("${app.auth.token-valid-days:30}") int tokenValidDays) {
        this.authTokenMapper = authTokenMapper;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl("https://api.weixin.qq.com").build();
        this.appId = appId;
        this.appSecret = appSecret;
        this.tokenValidDays = tokenValidDays;
    }

    public LoginResponse login(WechatLoginRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new BusinessException(400, "微信登录凭证不能为空");
        }
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new BusinessException(503, "服务端尚未配置微信小程序信息");
        }
        WechatSessionResponse session;
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                            .queryParam("appid", appId)
                            .queryParam("secret", appSecret)
                            .queryParam("js_code", request.code())
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve().body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new BusinessException(502, "微信登录响应为空，请稍后重试");
            }
            session = objectMapper.readValue(responseBody, WechatSessionResponse.class);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("调用微信 code2Session 接口失败", exception);
            throw new BusinessException(502, "无法连接微信登录服务，请稍后重试");
        }
        if (session == null) {
            throw new BusinessException(502, "微信登录响应为空，请稍后重试");
        }
        if (session.errcode() != null && session.errcode() != 0) {
            log.warn("微信 code2Session 失败，errcode={}, errmsg={}", session.errcode(), session.errmsg());
            throw new BusinessException(401, wechatErrorMessage(session.errcode()));
        }
        if (session.openid() == null || session.openid().isBlank()) {
            throw new BusinessException(401, "微信登录未返回用户标识，请重新进入小程序");
        }
        UserEntity user = userService.findOrCreate(session.openid(), session.unionid());
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        AuthTokenEntity tokenEntity = new AuthTokenEntity();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setTokenHash(hash(token));
        tokenEntity.setExpiresAt(OffsetDateTime.now().plusDays(tokenValidDays));
        tokenEntity.setCreatedAt(OffsetDateTime.now());
        authTokenMapper.insert(tokenEntity);
        return new LoginResponse(token, userService.toResponse(user));
    }

    public CurrentUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "请重新进入小程序");
        }
        AuthTokenEntity tokenEntity = authTokenMapper.selectOne(Wrappers.<AuthTokenEntity>lambdaQuery()
                .eq(AuthTokenEntity::getTokenHash, hash(token))
                .gt(AuthTokenEntity::getExpiresAt, OffsetDateTime.now()));
        if (tokenEntity == null) {
            throw new BusinessException(401, "登录状态已过期，请重新进入小程序");
        }
        UserEntity user = userService.findById(tokenEntity.getUserId());
        return new CurrentUser(user.getId(), user.getOpenid());
    }

    private String wechatErrorMessage(int errcode) {
        return switch (errcode) {
            case 40013 -> "微信小程序 AppID 无效或前后端 AppID 不一致";
            case 40125 -> "微信小程序 AppSecret 无效，请检查后端配置";
            case 40029 -> "微信登录凭证无效，请重新编译小程序后再试";
            case 45011 -> "微信登录请求过于频繁，请稍后再试";
            case -1 -> "微信系统繁忙，请稍后再试";
            default -> "微信登录失败，错误码：" + errcode;
        };
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
