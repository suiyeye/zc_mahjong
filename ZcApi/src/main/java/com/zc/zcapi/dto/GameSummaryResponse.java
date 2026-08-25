package com.zc.zcapi.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GameSummaryResponse(Long id, String joinCode, String status, Integer roundCount,
                                  OffsetDateTime createdAt, OffsetDateTime finishedAt, boolean owner,
                                  List<PlayerResponse> players) {
}
