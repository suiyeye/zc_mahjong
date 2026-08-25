package com.zc.zcapi.dto;

import com.zc.zcapi.dto.UserProfileResponse;

public record LoginResponse(String token, UserProfileResponse user) {
}
