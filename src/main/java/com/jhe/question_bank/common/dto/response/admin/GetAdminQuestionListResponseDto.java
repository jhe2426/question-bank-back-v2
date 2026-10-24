package com.jhe.question_bank.common.dto.response.admin;

import com.jhe.question_bank.common.dto.response.ResponseDto;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Getter
public class GetAdminQuestionListResponseDto extends ResponseDto {
    private final List<AdminQuestionListItemDto> questions;
    private final long totalElements;
    private final int totalPages;
    private final int page;
    private final int size;
    private final boolean last;

    private GetAdminQuestionListResponseDto(Page<AdminQuestionListItemDto> result) {
        this.questions = result.getContent();
        this.totalElements = result.getTotalElements();
        this.totalPages = result.getTotalPages();
        this.page = result.getNumber();
        this.size = result.getSize();
        this.last = result.isLast();
    }

    public static ResponseEntity<GetAdminQuestionListResponseDto> success(Page<AdminQuestionListItemDto> result) {
        return ResponseEntity.ok(new GetAdminQuestionListResponseDto(result));
    }
}
