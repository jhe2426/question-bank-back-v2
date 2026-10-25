package com.jhe.question_bank.common.dto.response.admin;

import com.jhe.question_bank.common.dto.response.ResponseDto;
import com.jhe.question_bank.common.entity.UserEntity;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Getter
public class GetAdminUserLearningSummaryResponseDto extends ResponseDto {
    private final String userId;
    private final String userName;
    private final String phoneNumber;
    private final long totalSolvedCount;
    private final Long totalSolveSeconds;
    private final List<SectionSummaryDto> sectionSummaries;

    @Getter
    public static class SectionSummaryDto {
        private final Integer sectionId;
        private final String sectionName;
        private final long solvedCount;
        private final long correctCount;
        private final double accuracyRate;

        public SectionSummaryDto(
                Integer sectionId,
                String sectionName,
                long solvedCount,
                long correctCount,
                double accuracyRate
        ) {
            this.sectionId = sectionId;
            this.sectionName = sectionName;
            this.solvedCount = solvedCount;
            this.correctCount = correctCount;
            this.accuracyRate = accuracyRate;
        }
    }

    private GetAdminUserLearningSummaryResponseDto(
            UserEntity user,
            long totalSolvedCount,
            Long totalSolveSeconds,
            List<SectionSummaryDto> sectionSummaries
    ) {
        this.userId = user.getUserId();
        this.userName = user.getName();
        this.phoneNumber = user.getPhoneNumber();
        this.totalSolvedCount = totalSolvedCount;
        this.totalSolveSeconds = totalSolveSeconds;
        this.sectionSummaries = List.copyOf(sectionSummaries);
    }

    public static ResponseEntity<GetAdminUserLearningSummaryResponseDto> success(
            UserEntity user,
            long totalSolvedCount,
            Long totalSolveSeconds,
            List<SectionSummaryDto> sectionSummaries
    ) {
        return ResponseEntity.ok(new GetAdminUserLearningSummaryResponseDto(
                user, totalSolvedCount, totalSolveSeconds, sectionSummaries
        ));
    }
}
