package com.zc.zcapi.common.response;

import java.time.OffsetDateTime;

public record ApiError(String message, OffsetDateTime timestamp) {
}
