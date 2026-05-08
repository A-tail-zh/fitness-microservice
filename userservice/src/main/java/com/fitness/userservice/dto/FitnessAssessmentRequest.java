package com.fitness.userservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FitnessAssessmentRequest {

    @NotNull(message = "年龄不能为空")
    @Min(value = 10, message = "年龄不能小于 10")
    @Max(value = 100, message = "年龄不能大于 100")
    private Integer age;

    @NotNull(message = "身高不能为空")
    @Min(value = 80, message = "身高不能小于 80cm")
    @Max(value = 250, message = "身高不能大于 250cm")
    private Double height;

    @NotNull(message = "体重不能为空")
    @Min(value = 20, message = "体重不能小于 20kg")
    @Max(value = 300, message = "体重不能大于 300kg")
    private Double weight;

    @NotBlank(message = "性别不能为空")
    private String gender;

    @NotBlank(message = "运动目标不能为空")
    private String goal;

    @NotNull(message = "每周运动频率不能为空")
    @Min(value = 0, message = "每周运动频率不能小于 0")
    @Max(value = 14, message = "每周运动频率不能大于 14")
    private Integer weeklyExerciseFrequency;

    @NotBlank(message = "最近一次运动时间不能为空")
    private String recentExerciseTime;

    private String injuryStatus;

    @NotBlank(message = "运动经验不能为空")
    private String exerciseExperience;
}
