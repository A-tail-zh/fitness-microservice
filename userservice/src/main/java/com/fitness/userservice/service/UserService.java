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

    /**
     * 用户注册或同步
     * <p>
     * 处理新用户注册请求或同步已有用户信息。首先根据邮箱查找用户：
     * - 如果用户已存在，则更新其Keycloak ID和姓名等信息；
     * - 如果用户不存在，则创建新的用户记录。
     *
     * @param request 注册请求对象，包含邮箱、密码、Keycloak ID、firstName、lastName等注册信息
     * @return 包含注册用户信息的UserResponse对象
     * @throws UserAlreadyExistsException 当邮箱已被其他Keycloak账号绑定时抛出
     */
    public UserResponse register(RegisterRequest request) {
        log.info("开始注册/同步用户，email={}, keycloakId={}", request.getEmail(), request.getKeycloakId());

        return userRepository.findByEmail(request.getEmail())
                .map(existingUser -> handleExistingUser(existingUser, request))
                .orElseGet(() -> createNewUser(request));
    }

    /**
     * 根据用户ID获取用户资料信息
     * <p>
     * 从数据库中查询指定ID的用户，并将其转换为响应对象返回。
     * 如果用户不存在，则抛出UserNotFoundException异常。
     *
     * @param userId 用户唯一标识符，用于定位要查询的用户
     * @return 包含用户详细信息的UserResponse对象，包括邮箱、姓名、创建时间等
     * @throws UserNotFoundException 当指定ID的用户在数据库中不存在时抛出
     */
    public UserResponse getUserProfileById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("未找到用户，id=" + userId));
        return toResponse(user);
    }

    /**
     * 根据Keycloak ID获取用户资料信息
     * <p>
     * 从数据库中查询指定Keycloak ID的用户，并将其转换为响应对象返回。
     * 如果用户不存在，则抛出UserNotFoundException异常。
     *
     * @param keycloakId Keycloak身份标识符，用于定位要查询的用户
     * @return 包含用户详细信息的UserResponse对象，包括邮箱、姓名、创建时间等
     * @throws UserNotFoundException 当指定Keycloak ID的用户在数据库中不存在时抛出
     */
    public UserResponse getUserProfileByKeycloakId(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("未找到用户，keycloakId=" + keycloakId));
        return toResponse(user);
    }

    /**
     * 验证用户是否存在
     * <p>
     * 检查指定的用户ID或Keycloak ID在系统中是否存在对应的用户记录。
     *
     * @param userId 用户唯一标识符或Keycloak ID，用于验证用户是否存在
     * @return Boolean值，true表示用户存在，false表示用户不存在
     */
    public Boolean existByUserId(String userId) {
        log.info("校验用户是否存在，identifier={}", userId);
        return userRepository.existsById(userId) || userRepository.existsByKeycloakId(userId);
    }

    /**
     * 处理已有用户的注册请求
     * <p>
     * 当邮箱已存在时，验证Keycloak ID的一致性并更新用户信息。
     * 如果现有用户已绑定不同的Keycloak账号，则抛出异常；
     * 否则补充缺失的Keycloak ID、firstName和lastName等信息。
     *
     * @param existingUser 数据库中已存在的用户对象
     * @param request      注册请求对象，包含需要更新的用戶信息
     * @return 更新后的用户响应对象UserResponse
     * @throws UserAlreadyExistsException 当邮箱已绑定其他Keycloak账号时抛出
     */
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

    /**
     * 创建新用户
     * <p>
     * 根据注册请求信息创建新的用户记录。该方法会：
     * - 从RegisterRequest中提取用户的邮箱、密码、Keycloak ID、firstName和lastName等信息；
     * - 创建User对象并保存到数据库；
     * - 记录新用户创建成功的日志；
     * - 将User对象转换为UserResponse返回。
     *
     * @param request 注册请求对象，包含新用户的邮箱、密码、Keycloak ID、firstName、lastName等信息
     * @return 包含新用户信息的UserResponse对象，包括用户ID、邮箱、姓名、创建时间等
     */
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
