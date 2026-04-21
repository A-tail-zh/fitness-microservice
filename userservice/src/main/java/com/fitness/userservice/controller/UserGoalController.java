package com.fitness.userservice.controller;

import com.fitness.userservice.dto.UserGoalRequest;
import com.fitness.userservice.dto.UserGoalResponse;
import com.fitness.userservice.dto.UserGoalStatusUpdateRequest;
import com.fitness.userservice.service.UserGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/goals")
@RequiredArgsConstructor
public class UserGoalController {

    private final UserGoalService userGoalService;


    /**
     * 创建用户健身目标
     * <p>
     * 为指定用户创建新的健身目标。该接口接收用户ID和目标请求信息，
     * 调用服务层创建目标记录，并返回HTTP 201状态码及创建的目标信息。
     *
     * @param userId  用户唯一标识符，从URL路径变量中获取
     * @param request 用户目标请求对象，包含目标的标题、描述、目标值、截止日期等信息，需通过验证
     * @return ResponseEntity包装的UserGoalResponse对象，包含创建成功的目标信息，HTTP状态码为201 CREATED
     */
    @PostMapping
    public ResponseEntity<UserGoalResponse> createGoal(@PathVariable String userId,
                                                       @Valid @RequestBody UserGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userGoalService.createGoal(userId, request));
    }


    /**
     * 获取用户当前活动目标
     * <p>
     * 获取指定用户当前活动目标。该接口接收用户ID，调用服务层获取当前活动目标信息，并返回HTTP 200状态码及目标信息。
     *
     * @param userId 用户唯一标识符，从URL路径变量中获取
     * @return ResponseEntity包装的UserGoalResponse对象，包含当前活动目标信息，HTTP状态码为200 OK
     */
    @GetMapping("/active")
    public ResponseEntity<UserGoalResponse> getActiveGoal(@PathVariable String userId) {
        return ResponseEntity.ok(userGoalService.getActiveGoal(userId));
    }


    /**
     * 获取用户所有目标
     * <p>
     * 获取指定用户所有目标。该接口接收用户ID，调用服务层获取所有目标信息，并返回HTTP 200状态码及目标信息列表。
     *
     * @param userId 用户唯一标识符，从URL路径变量中获取
     * @return ResponseEntity包装的List<UserGoalResponse>对象，包含所有目标信息列表，HTTP状态码为200 OK
     */
    @GetMapping
    public ResponseEntity<List<UserGoalResponse>> getAllGoals(@PathVariable String userId) {
        return ResponseEntity.ok(userGoalService.getAllGoals(userId));
    }


    /**
     * 更新用户健身目标
     * <p>
     * 根据用户ID和目标ID更新指定的健身目标信息。该接口接收完整的目标更新请求，
     * 调用服务层执行更新操作，并返回更新后的目标信息。
     *
     * @param userId  用户唯一标识符，从URL路径变量中获取
     * @param goalId  目标唯一标识符，从URL路径变量中获取，用于定位要更新的目标
     * @param request 用户目标请求对象，包含更新后的目标信息（如标题、描述、目标值、截止日期等），需通过验证
     * @return ResponseEntity包装的UserGoalResponse对象，包含更新后的目标信息，HTTP状态码为200 OK
     */
    @PutMapping("/{goalId}")
    public ResponseEntity<UserGoalResponse> updateGoal(@PathVariable String userId,
                                                       @PathVariable String goalId,
                                                       @Valid @RequestBody UserGoalRequest request) {
        return ResponseEntity.ok(userGoalService.updateGoal(userId, goalId, request));
    }


    /**
     * 更新用户健身目标状态
     * <p>
     * 根据用户ID和目标ID更新指定健身目标的状态。该接口用于部分更新目标的状态字段，
     * 例如将目标标记为进行中、已完成或已暂停等，调用服务层执行状态更新操作。
     *
     * @param userId  用户唯一标识符，从URL路径变量中获取
     * @param goalId  目标唯一标识符，从URL路径变量中获取，用于定位要更新状态的目标
     * @param request 用户目标状态更新请求对象，包含需要更新的目标状态信息，需通过验证
     * @return ResponseEntity包装的UserGoalResponse对象，包含更新状态后的目标信息，HTTP状态码为200 OK
     */
    @PatchMapping("/{goalId}/status")
    public ResponseEntity<UserGoalResponse> updateGoalStatus(@PathVariable String userId,
                                                             @PathVariable String goalId,
                                                             @Valid @RequestBody UserGoalStatusUpdateRequest request) {
        return ResponseEntity.ok(userGoalService.updateGoalStatus(userId, goalId, request.getStatus()));
    }
}