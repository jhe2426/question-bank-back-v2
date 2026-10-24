package com.jhe.question_bank.common.dto.response.admin;

import com.jhe.question_bank.common.dto.response.ResponseDto;
import com.jhe.question_bank.common.entity.DetailedExplanationEntity;
import com.jhe.question_bank.common.entity.QuestionEntity;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Getter
public class GetAdminQuestionDetailResponseDto extends ResponseDto {
    private final Integer questionId;
    private final String questionType;
    private final Integer sectionId;
    private final String difficulty;
    private final String questionText;
    private final String imageUrl;
    private final String option1;
    private final String option2;
    private final String option3;
    private final String option4;
    private final Integer answer;
    private final String shortExplanation;
    private final DetailedExplanationDto detailedExplanation;
    private final Integer pastExamRound;
    private final Integer questionOrder;
    private final List<Integer> tags;

    public record DetailedExplanationDto(String content, String imageUrl) {}

    private GetAdminQuestionDetailResponseDto(QuestionEntity question, DetailedExplanationEntity explanation,
                                               Integer pastExamRound, Integer questionOrder, List<Integer> tags) {
        this.questionId = question.getQuestionId();
        this.questionType = question.getQuestionType();
        this.sectionId = question.getSectionId();
        this.difficulty = question.getDifficulty();
        this.questionText = question.getQuestionText();
        this.imageUrl = question.getImageUrl();
        this.option1 = question.getOption1();
        this.option2 = question.getOption2();
        this.option3 = question.getOption3();
        this.option4 = question.getOption4();
        this.answer = question.getAnswer();
        this.shortExplanation = question.getShortExplanation();
        this.detailedExplanation = explanation == null ? null
                : new DetailedExplanationDto(explanation.getContent(), explanation.getImageUrl());
        this.pastExamRound = pastExamRound;
        this.questionOrder = questionOrder;
        this.tags = List.copyOf(tags);
    }

    public static ResponseEntity<GetAdminQuestionDetailResponseDto> success(
            QuestionEntity question, DetailedExplanationEntity explanation,
            Integer pastExamRound, Integer questionOrder, List<Integer> tags) {
        return ResponseEntity.ok(new GetAdminQuestionDetailResponseDto(
                question, explanation, pastExamRound, questionOrder, tags));
    }
}
