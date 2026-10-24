package com.jhe.question_bank.common.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminUserListItemDto {
    private final String userId;
    private final String userName;
    private final String phoneNumber;
}
