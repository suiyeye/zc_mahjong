package com.zc.zcapi.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RoundResponse(Long id, Integer roundNo, String note, OffsetDateTime createdAt,
                            List<RoundScoreResponse> scores) {
    public record RoundScoreResponse(Long playerId, String playerName, Integer delta, Integer scoreAfter) {
    }
}
