package com.zc.zcapi.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GameDetailResponse(Long id, String joinCode, String status, Integer roundCount,
                                 OffsetDateTime createdAt, OffsetDateTime finishedAt,
                                 boolean owner, Long currentPlayerId,
                                 List<PlayerResponse> players, List<RoundResponse> rounds,
                                 List<GameEventResponse> events) {
}
