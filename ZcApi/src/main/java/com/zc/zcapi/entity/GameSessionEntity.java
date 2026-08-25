package com.zc.zcapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("game_session")
public class GameSessionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private String joinCode;
    private String inviteToken;
    private String status;
    private Integer roundCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime finishedAt;
}
