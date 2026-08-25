package com.zc.zcapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.zcapi.entity.AuthTokenEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthTokenMapper extends BaseMapper<AuthTokenEntity> {
}
