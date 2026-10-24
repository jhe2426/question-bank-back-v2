package com.jhe.question_bank.common.dto.request.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetAdminQuestionsRequestDto {
    @NotBlank
    private String questionType;
    private Integer pastExamRound;
    private String difficulty;
    @Min(0)
    private int page = 0;
    @Min(1)
    @Max(100)
    private int size = 20;
}
