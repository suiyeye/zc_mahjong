package com.zc.zcapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.zcapi.entity.GameSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GameSessionMapper extends BaseMapper<GameSessionEntity> {

    @Select("SELECT * FROM game_session WHERE id = #{gameId} FOR UPDATE")
    GameSessionEntity selectByIdForUpdate(long gameId);
}
