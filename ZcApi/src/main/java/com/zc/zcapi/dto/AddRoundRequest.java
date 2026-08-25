package com.zc.zcapi.dto;

import java.util.List;

public record AddRoundRequest(String note, List<ScoreInput> scores) {
    public record ScoreInput(Long playerId, Integer delta) {
    }
}
