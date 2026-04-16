package com.fitness.userservice.exception;

public class UserGoalNotFoundException extends RuntimeException {
    public UserGoalNotFoundException(String message) {
        super(message);
    }
}