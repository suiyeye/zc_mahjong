package com.zc.zcapi.dto;

import java.time.OffsetDateTime;

public record GameEventResponse(Long id, String eventType, Long playerId, String playerName,
                                Long targetPlayerId, String targetPlayerName, Integer amount,
                                Integer senderScoreAfter, Integer targetScoreAfter,
                                Integer senderScoreBefore, Integer targetScoreBefore,
                                OffsetDateTime createdAt) {
}
