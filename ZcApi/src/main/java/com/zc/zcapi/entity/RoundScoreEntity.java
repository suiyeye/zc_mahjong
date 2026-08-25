package com.zc.zcapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("round_score")
public class RoundScoreEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roundId;
    private Long playerId;
    private Integer scoreDelta;
}
