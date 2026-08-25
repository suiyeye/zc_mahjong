package com.zc.zcapi.dto;

public record PlayerResponse(Long id, Long userId, Integer playerOrder, String name,
                             String avatarUrl, Integer currentScore) {
}
