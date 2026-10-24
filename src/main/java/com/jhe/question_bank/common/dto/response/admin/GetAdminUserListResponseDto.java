package com.jhe.question_bank.common.dto.response.admin;

import com.jhe.question_bank.common.dto.response.ResponseDto;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Getter
public class GetAdminUserListResponseDto extends ResponseDto {
    private final List<AdminUserListItemDto> users;
    private final long totalElements;
    private final int totalPages;
    private final int page;
    private final int size;
    private final boolean last;

    private GetAdminUserListResponseDto(Page<AdminUserListItemDto> result) {
        this.users = result.getContent();
        this.totalElements = result.getTotalElements();
        this.totalPages = result.getTotalPages();
        this.page = result.getNumber();
        this.size = result.getSize();
        this.last = result.isLast();
    }

    public static ResponseEntity<GetAdminUserListResponseDto> success(Page<AdminUserListItemDto> result) {
        return ResponseEntity.ok(new GetAdminUserListResponseDto(result));
    }
}
