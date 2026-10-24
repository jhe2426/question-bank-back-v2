package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.dto.request.admin.GetAdminQuestionsRequestDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminQuestionListResponseDto;
import com.jhe.question_bank.common.exception.BusinessException;
import com.jhe.question_bank.common.exception.ErrorCode;
import com.jhe.question_bank.repository.PastExamQuestionRepository;
import com.jhe.question_bank.repository.QuestionRepository;
import com.jhe.question_bank.repository.DetailedExplanationRepository;
import com.jhe.question_bank.repository.MockPastTagRepository;
import com.jhe.question_bank.common.dto.response.admin.GetAdminQuestionDetailResponseDto;
import com.jhe.question_bank.common.entity.QuestionEntity;
import com.jhe.question_bank.common.entity.DetailedExplanationEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQuestionQueryService {
    private final QuestionRepository questionRepository;
    private final PastExamQuestionRepository pastExamQuestionRepository;

    private final DetailedExplanationRepository detailedExplanationRepository;
    private final MockPastTagRepository mockPastTagRepository;

    public ResponseEntity<GetAdminQuestionDetailResponseDto> getQuestion(Integer questionId, Integer pastExamRound) {
        QuestionEntity question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_QUESTION_NOT_FOUND));
        DetailedExplanationEntity explanation = question.getDetailExplanationId() == null ? null
                : detailedExplanationRepository.findById(question.getDetailExplanationId()).orElse(null);
        Integer questionOrder = null;
        List<Integer> tags = List.of();
        if ("기출문제".equals(question.getQuestionType())) {
            var pastExam = pastExamQuestionRepository
                    .findAdminPastExamDetails(questionId, pastExamRound)
                    .orElseThrow(() ->
                            new BusinessException(ErrorCode.ADMIN_QUESTION_NOT_FOUND)
                    );

            questionOrder = pastExam.getQuestionOrder();
        } else if ("모의고사".equals(question.getQuestionType())) {
            tags = mockPastTagRepository.findPastExamRoundsByQuestionId(questionId);
        }
        return GetAdminQuestionDetailResponseDto.success(question, explanation, pastExamRound, questionOrder, tags);
    }

    public ResponseEntity<GetAdminQuestionListResponseDto> getQuestions(GetAdminQuestionsRequestDto request) {
        if (request.getPage() < 0 || request.getSize() < 1 || request.getSize() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAIL);
        }
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        if ("기출문제".equals(request.getQuestionType())) {
            if (request.getPastExamRound() == null || request.getPastExamRound() < 1) {
                throw new BusinessException(ErrorCode.VALIDATION_FAIL);
            }
            return GetAdminQuestionListResponseDto.success(
                    pastExamQuestionRepository.findAdminQuestionsByRound(request.getPastExamRound(), pageable));
        }
        if ("모의고사".equals(request.getQuestionType())) {
            String difficulty = request.getDifficulty();
            if (!"상".equals(difficulty) && !"중".equals(difficulty) && !"하".equals(difficulty)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAIL);
            }
            return GetAdminQuestionListResponseDto.success(
                    questionRepository.findAdminMockQuestions(difficulty, pageable));
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAIL);
    }
}
