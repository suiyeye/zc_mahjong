package com.zc.zcapi.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zc.zcapi.common.exception.BusinessException;
import com.zc.zcapi.dto.RoomInviteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class QrCodeService {

    private static final Logger log = LoggerFactory.getLogger(QrCodeService.class);
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String appId;
    private final String appSecret;
    private final String envVersion;
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public QrCodeService(ObjectMapper objectMapper,
                         @Value("${wechat.app-id:}") String appId,
                         @Value("${wechat.app-secret:}") String appSecret,
                         @Value("${wechat.mini-program.env-version:develop}") String envVersion) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl("https://api.weixin.qq.com").build();
        this.appId = appId;
        this.appSecret = appSecret;
        if (!envVersion.matches("develop|trial|release")) {
            throw new IllegalArgumentException("微信小程序码版本必须是 develop、trial 或 release");
        }
        this.envVersion = envVersion;
    }

    public RoomInviteResponse createRoomInvite(String inviteToken) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new BusinessException(503, "服务端尚未配置微信小程序信息");
        }
        try {
            String token = getAccessToken();
            byte[] requestBody = objectMapper.writeValueAsBytes(Map.of(
                    "scene", inviteToken,
                    "page", "pages/index/index",
                    "width", 360,
                    "check_path", false,
                    "env_version", envVersion));
            byte[] image = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/wxa/getwxacodeunlimit")
                            .queryParam("access_token", token)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.length)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            if (image == null || image.length == 0) {
                throw new BusinessException(502, "微信未返回小程序码");
            }
            if (image[0] == '{') {
                WechatResponse error = objectMapper.readValue(image, WechatResponse.class);
                log.warn("生成微信小程序码失败，errcode={}, errmsg={}", error.errcode(), error.errmsg());
                throw new BusinessException(502, "生成微信小程序码失败，错误码：" + error.errcode());
            }
            return new RoomInviteResponse("data:image/png;base64," + Base64.getEncoder().encodeToString(image));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("调用微信小程序码接口失败", exception);
            throw new BusinessException(502, "无法连接微信小程序码服务，请稍后重试");
        }
    }

    private synchronized String getAccessToken() throws Exception {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }
        String responseBody = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", appId)
                        .queryParam("secret", appSecret)
                        .build())
                .retrieve()
                .body(String.class);
        WechatResponse response = objectMapper.readValue(responseBody, WechatResponse.class);
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            log.warn("获取微信 access_token 失败，errcode={}, errmsg={}", response.errcode(), response.errmsg());
            throw new BusinessException(502, "获取微信小程序凭证失败，错误码：" + response.errcode());
        }
        accessToken = response.accessToken();
        accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, response.expiresIn() - 300));
        return accessToken;
    }

    private record WechatResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Integer expiresIn,
            Integer errcode,
            String errmsg) {
    }
}
