package com.zc.zcapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("game_round")
public class GameRoundEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Integer roundNo;
    private String note;
    private OffsetDateTime createdAt;
}
