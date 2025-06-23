package com.fitness.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "电子邮件是必需的")
    @Email(message = "电子邮件格式无效")
    private String email;

    @NotBlank(message = "密码是必需的")
    @Size(min = 6, message = "密码长度必须至少为6个字符")
    private String password;
    private String firstName;
    private String lastName;//姓氏


}
