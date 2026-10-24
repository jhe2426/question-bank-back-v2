package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.dto.request.admin.GetAdminQuestionsRequestDto;
import com.jhe.question_bank.common.dto.response.admin.GetAdminQuestionListResponseDto;
import com.jhe.question_bank.common.exception.BusinessException;
import com.jhe.question_bank.common.exception.ErrorCode;
import com.jhe.question_bank.repository.PastExamQuestionRepository;
import com.jhe.question_bank.repository.QuestionRepository;
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
