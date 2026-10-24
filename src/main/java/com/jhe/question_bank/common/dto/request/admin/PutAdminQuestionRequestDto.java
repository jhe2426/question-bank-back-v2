package com.jhe.question_bank.common.dto.request.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class PutAdminQuestionRequestDto {
    @NotBlank @Size(max = 255)
    private String questionText;
    @NotBlank @Size(max = 255)
    private String option1;
    @NotBlank @Size(max = 255)
    private String option2;
    @NotBlank @Size(max = 255)
    private String option3;
    @NotBlank @Size(max = 255)
    private String option4;
    @NotNull @Min(1) @Max(4)
    private Integer answer;
    @NotNull @Pattern(regexp = "상|중|하")
    private String difficulty;
    @NotNull @Min(1)
    private Integer sectionId;
    @Size(max = 255)
    private String shortExplanation;
    @Valid
    private Explanation detailedExplanation;
    private boolean deleteQuestionImage;
    private boolean deleteExplanationImage;
    @Min(1)
    private Integer pastExamRound;
    @Min(1)
    private Integer questionOrder;
    private List<@NotNull @Min(1) Integer> tags = List.of();

    @Getter @Setter
    public static class Explanation {
        @Size(max = 255)
        private String content;
    }
}
