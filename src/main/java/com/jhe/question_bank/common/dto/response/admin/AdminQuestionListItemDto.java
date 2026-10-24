package com.jhe.question_bank.common.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminQuestionListItemDto {
    private final Integer questionId;
    private final String questionType;
    private final String questionText;
    private final String difficulty;
    private final String imageUrl;
    private final Integer sectionId;
    private final Integer pastExamRound;
    private final Integer questionOrder;

    public AdminQuestionListItemDto(Integer questionId, String questionType, String questionText,
                                    String difficulty, String imageUrl, Integer sectionId) {
        this(questionId, questionType, questionText, difficulty, imageUrl, sectionId, null, null);
    }
}
