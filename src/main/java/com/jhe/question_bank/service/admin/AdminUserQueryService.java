package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.dto.request.admin.GetAdminUsersRequestDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminUserLearningSummaryResponseDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminUserLearningSummaryResponseDto.SectionSummaryDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminUserListResponseDto;
import com.jhe.question_bank.common.entity.UserEntity;
import com.jhe.question_bank.common.exception.BusinessException;
import com.jhe.question_bank.common.exception.ErrorCode;
import com.jhe.question_bank.enums.Role;
import com.jhe.question_bank.repository.UserProblemGroupRepository;
import com.jhe.question_bank.repository.UserRepository;
import com.jhe.question_bank.repository.UserSolvedHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserQueryService {
    private final UserRepository userRepository;
    private final UserSolvedHistoryRepository solvedHistoryRepository;
    private final UserProblemGroupRepository problemGroupRepository;

    public ResponseEntity<GetAdminUserListResponseDto> getUsers(GetAdminUsersRequestDto request) {
        String keyword = normalizeKeyword(request.getKeyword());
        return GetAdminUserListResponseDto.success(
                userRepository.findAdminUserPage(
                        Role.USER,
                        keyword,
                        normalizePhoneKeyword(keyword),
                        PageRequest.of(request.getPage(), request.getSize())
                )
        );
    }

    public ResponseEntity<GetAdminUserLearningSummaryResponseDto> getLearningSummary(String userId) {
        UserEntity user = userRepository.findByUserIdAndRole(userId, Role.USER)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND));

        long totalSolvedCount = solvedHistoryRepository.countDistinctSolvedQuestionsByUserId(userId);
        Long totalSolveSeconds = problemGroupRepository.sumTimeSpentSecondsByUserId(userId);
        var sectionSummaries = solvedHistoryRepository.findSectionLearningSummaries(userId).stream()
                .map(summary -> new SectionSummaryDto(
                        summary.getSectionId(),
                        summary.getSectionName(),
                        summary.getSolvedCount(),
                        summary.getCorrectCount(),
                        calculateAccuracyRate(summary.getCorrectCount(), summary.getSolvedCount())
                ))
                .toList();

        return GetAdminUserLearningSummaryResponseDto.success(
                user,
                totalSolvedCount,
                totalSolveSeconds,
                sectionSummaries
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return keyword.trim();
    }

    private String normalizePhoneKeyword(String keyword) {
        if (keyword == null) return null;
        String digits = keyword.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }

    private double calculateAccuracyRate(long correctCount, long solvedCount) {
        if (solvedCount == 0) return 0.0;
        return (double) correctCount / solvedCount * 100;
    }
}
