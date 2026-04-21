package com.fitness.userservice.controller;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    /**
     * 根据用户ID获取用户资料信息
     * <p>
     * 通过用户唯一标识符查询并返回该用户的详细资料信息。
     *
     * @param userId 用户唯一标识符，用于定位要查询的用户
     * @return 包含用户详细信息的UserResponse对象，HTTP状态码为200(OK)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfileById(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserProfileById(userId));
    }

    /**
     * 根据Keycloak ID获取用户资料信息
     * <p>
     * 通过Keycloak身份标识符查询并返回该用户的详细资料信息。
     *
     * @param keycloakId Keycloak身份标识符，用于定位要查询的用户
     * @return 包含用户详细信息的UserResponse对象，HTTP状态码为200(OK)
     */
    @GetMapping("/keycloak/{keycloakId}")
    public ResponseEntity<UserResponse> getUserProfileByKeycloakId(@PathVariable String keycloakId) {
        return ResponseEntity.ok(userService.getUserProfileByKeycloakId(keycloakId));
    }

    /**
     * 用户注册接口
     * <p>
     * 处理新用户注册请求或同步已有用户信息。如果邮箱已存在，则更新用户信息；
     * 否则创建新用户账户。
     *
     * @param request 注册请求对象，包含邮箱、密码、Keycloak ID、姓名等注册信息，
     *                该参数必须通过@Valid验证
     * @return 包含注册用户信息的UserResponse对象，HTTP状态码为200(OK)
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    /**
     * 验证用户是否存在
     * <p>
     * 检查指定的用户ID或Keycloak ID在系统中是否存在对应的用户记录。
     *
     * @param userId 用户唯一标识符或Keycloak ID，用于验证用户是否存在
     * @return Boolean值，true表示用户存在，false表示用户不存在，HTTP状态码为200(OK)
     */
    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.existByUserId(userId));
    }
}