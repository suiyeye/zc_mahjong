package com.zc.zcapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zc.zcapi.common.exception.BusinessException;
import com.zc.zcapi.dto.UpdateProfileRequest;
import com.zc.zcapi.dto.UserProfileResponse;
import com.zc.zcapi.entity.UserEntity;
import com.zc.zcapi.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserEntity findOrCreate(String openid, String unionid) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getOpenid, openid));
        if (user != null) {
            if (user.getUnionid() == null && unionid != null && !unionid.isBlank()) {
                user.setUnionid(unionid);
                user.setUpdatedAt(OffsetDateTime.now());
                userMapper.updateById(user);
            }
            return user;
        }
        user = new UserEntity();
        user.setOpenid(openid);
        user.setUnionid(unionid);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public UserEntity findById(long userId) {
        return requireUser(userId);
    }

    public UserProfileResponse getProfile(long userId) {
        UserEntity user = requireUser(userId);
        return toResponse(user);
    }

    public UserProfileResponse updateProfile(long userId, UpdateProfileRequest request) {
        if (request == null || request.nickname() == null || request.nickname().isBlank()
                || request.nickname().trim().length() > 20) {
            throw new BusinessException(400, "昵称不能为空且不能超过 20 个字");
        }
        if (request.avatarUrl() == null || request.avatarUrl().isBlank()) {
            throw new BusinessException(400, "请选择头像");
        }
        UserEntity user = requireUser(userId);
        user.setNickname(request.nickname().trim());
        user.setAvatarUrl(request.avatarUrl().trim());
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
        return toResponse(user);
    }

    private UserEntity requireUser(long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在，请重新进入小程序");
        }
        return user;
    }

    public UserProfileResponse toResponse(UserEntity user) {
        boolean completed = user.getNickname() != null && !user.getNickname().isBlank()
                && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getAvatarUrl(), completed);
    }
}
