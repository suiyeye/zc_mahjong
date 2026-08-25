package com.zc.zcapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.zcapi.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
