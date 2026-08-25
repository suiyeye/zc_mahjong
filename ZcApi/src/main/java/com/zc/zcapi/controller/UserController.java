package com.zc.zcapi.controller;

import com.zc.zcapi.common.CurrentUserHolder;
import com.zc.zcapi.dto.UpdateProfileRequest;
import com.zc.zcapi.dto.UserProfileResponse;
import com.zc.zcapi.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserProfileResponse getProfile() {
        return userService.getProfile(CurrentUserHolder.require().id());
    }

    @PutMapping
    public UserProfileResponse updateProfile(@RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUserHolder.require().id(), request);
    }
}
