package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    public UserResponse register(RegisterRequest request) {

        if(userRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("电子邮件已存在");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        User savedUser = userRepository.save(user);
        return new UserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getPassword(),
                savedUser.getFirstName(), savedUser.getLastName(), savedUser.getCreatedAt(), savedUser.getUpdatedAt());
    }

    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        return new UserResponse(user.getId(), user.getEmail(), user.getPassword(),
                user.getFirstName(), user.getLastName(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
