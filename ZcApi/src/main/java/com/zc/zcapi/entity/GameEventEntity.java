package com.zc.zcapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("game_event")
public class GameEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private String eventType;
    private Long playerId;
    private String playerName;
    private Long targetPlayerId;
    private String targetPlayerName;
    private Integer amount;
    private OffsetDateTime createdAt;
}
