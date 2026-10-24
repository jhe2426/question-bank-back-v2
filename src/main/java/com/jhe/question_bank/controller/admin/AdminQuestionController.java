package com.jhe.question_bank.controller.admin;

import com.jhe.question_bank.common.dto.response.ResponseDto;
import com.jhe.question_bank.common.dto.request.admin.GetAdminQuestionsRequestDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminQuestionListResponseDto;
import com.jhe.question_bank.service.admin.AdminQuestionQueryService;
import jakarta.validation.Valid;
import com.jhe.question_bank.service.admin.AdminQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final AdminQuestionService adminQuestionService;

    private final AdminQuestionQueryService adminQuestionQueryService;

    @GetMapping
    public ResponseEntity<GetAdminQuestionListResponseDto> getQuestions(
            @Valid @ModelAttribute GetAdminQuestionsRequestDto request
    ) {
        return adminQuestionQueryService.getQuestions(request);
    }

    @PostMapping
    public ResponseEntity<ResponseDto> postQuestions(
            @RequestPart MultipartFile jsonFile,
            @RequestPart(value = "images", required = false)
            List<MultipartFile> images
    ) {

        ResponseEntity<ResponseDto> response = adminQuestionService.postQuestions(jsonFile, images);
        return response;

    }

}
