package com.fitness.aiservice.controller;

import com.fitness.aiservice.dto.FitnessAssessmentAnalysisRequest;
import com.fitness.aiservice.dto.FitnessAssessmentAnalysisResponse;
import com.fitness.aiservice.service.FitnessAssessmentAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/ai-analysis")
@RequiredArgsConstructor
public class FitnessAssessmentAnalysisController {

    private final FitnessAssessmentAnalysisService fitnessAssessmentAnalysisService;

    @PostMapping("/fitness-assessment")
    public ResponseEntity<FitnessAssessmentAnalysisResponse> analyze(
            @RequestBody FitnessAssessmentAnalysisRequest request) {
        return ResponseEntity.ok(fitnessAssessmentAnalysisService.analyze(request));
    }
}
