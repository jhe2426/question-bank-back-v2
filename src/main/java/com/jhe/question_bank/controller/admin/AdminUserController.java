package com.jhe.question_bank.controller.admin;

import com.jhe.question_bank.common.dto.request.admin.GetAdminUsersRequestDto;
import com.jhe.question_bank.common.dto.response.ResponseDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminUserLearningSummaryResponseDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminUserListResponseDto;
import com.jhe.question_bank.service.admin.AdminUserQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserQueryService adminUserQueryService;

    @GetMapping
    public ResponseEntity<GetAdminUserListResponseDto> getUsers(
            @Valid @ModelAttribute GetAdminUsersRequestDto request
    ) {
        return adminUserQueryService.getUsers(request);
    }

    @GetMapping("/{userId}/learning-summary")
    public ResponseEntity<GetAdminUserLearningSummaryResponseDto> getLearningSummary(
            @PathVariable("userId") String userId
    ) {
        return adminUserQueryService.getLearningSummary(userId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseDto> invalidQueryParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseDto.validationFail();
    }
}
