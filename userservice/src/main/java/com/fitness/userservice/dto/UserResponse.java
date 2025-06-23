package com.fitness.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String password;
    private String firstName;//名
    private String lastName;//姓
    private LocalDate createdAt;
    private LocalDate updatedAt;
}
