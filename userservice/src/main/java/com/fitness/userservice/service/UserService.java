package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.exception.UserAlreadyExistsException;
import com.fitness.userservice.exception.UserNotFoundException;
import com.fitness.userservice.model.User;
import com.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse register(RegisterRequest request) {
        log.info("开始注册/同步用户，email={}, keycloakId={}", request.getEmail(), request.getKeycloakId());

        return userRepository.findByEmail(request.getEmail())
                .map(existingUser -> handleExistingUser(existingUser, request))
                .orElseGet(() -> createNewUser(request));
    }

    public UserResponse getUserProfileById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("未找到用户，id=" + userId));
        return toResponse(user);
    }

    public UserResponse getUserProfileByKeycloakId(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("未找到用户，keycloakId=" + keycloakId));
        return toResponse(user);
    }

    public Boolean existByUserId(String userId) {
        log.info("校验用户是否存在，keycloakId={}", userId);
        return userRepository.existsByKeycloakId(userId);
    }

    private UserResponse handleExistingUser(User existingUser, RegisterRequest request) {
        if (StringUtils.hasText(existingUser.getKeycloakId())
                && StringUtils.hasText(request.getKeycloakId())
                && !existingUser.getKeycloakId().equals(request.getKeycloakId())) {
            throw new UserAlreadyExistsException("该邮箱已绑定其他 Keycloak 账号: " + request.getEmail());
        }

        boolean changed = false;

        if (!StringUtils.hasText(existingUser.getKeycloakId()) && StringUtils.hasText(request.getKeycloakId())) {
            existingUser.setKeycloakId(request.getKeycloakId());
            changed = true;
        }
        if (StringUtils.hasText(request.getFirstName()) && !request.getFirstName().equals(existingUser.getFirstName())) {
            existingUser.setFirstName(request.getFirstName());
            changed = true;
        }
        if (StringUtils.hasText(request.getLastName()) && !request.getLastName().equals(existingUser.getLastName())) {
            existingUser.setLastName(request.getLastName());
            changed = true;
        }

        User savedUser = changed ? userRepository.save(existingUser) : existingUser;
        log.info("复用已有用户，email={}, keycloakId={}", savedUser.getEmail(), savedUser.getKeycloakId());
        return toResponse(savedUser);
    }

    private UserResponse createNewUser(RegisterRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setKeycloakId(request.getKeycloakId());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        User savedUser = userRepository.save(user);
        log.info("新用户创建成功，email={}, keycloakId={}", savedUser.getEmail(), savedUser.getKeycloakId());
        return toResponse(savedUser);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
