package com.zc.zcapi.dto;

public record UserProfileResponse(Long id, String nickname, String avatarUrl, boolean profileCompleted) {
}
