package com.zc.zcapi.controller;

import com.zc.zcapi.dto.LoginResponse;
import com.zc.zcapi.dto.WechatLoginRequest;
import com.zc.zcapi.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wechat-login")
    public LoginResponse wechatLogin(@RequestBody WechatLoginRequest request) {
        return authService.login(request);
    }
}
