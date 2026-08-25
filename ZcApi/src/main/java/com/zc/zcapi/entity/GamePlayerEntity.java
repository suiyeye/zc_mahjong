package com.zc.zcapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("game_player")
public class GamePlayerEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Long userId;
    private Integer playerOrder;
    private String name;
    private String avatarUrl;
    private Integer currentScore;
    private Boolean hidden;
}
