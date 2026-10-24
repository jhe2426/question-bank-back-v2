package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.dto.response.ResponseDto;
import com.jhe.question_bank.common.entity.DetailedExplanationEntity;
import com.jhe.question_bank.common.entity.QuestionEntity;
import com.jhe.question_bank.common.exception.BusinessException;
import com.jhe.question_bank.common.exception.ErrorCode;
import com.jhe.question_bank.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminQuestionDeleteService {
    private final QuestionRepository questionRepository;
    private final DetailedExplanationRepository explanationRepository;
    private final MockPastTagRepository tagRepository;
    private final PastExamQuestionRepository pastExamQuestionRepository;
    private final UserSolvedHistoryRepository solvedHistoryRepository;
    private final UserIncorrectQuestionRepository incorrectQuestionRepository;
    private final GroupQuestionRepository groupQuestionRepository;
    private final AdminImageCleanupService imageCleanup;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<ResponseDto> deleteQuestion(Integer questionId) {
        QuestionEntity question = questionRepository.findForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_QUESTION_NOT_FOUND));
        if (solvedHistoryRepository.existsByQuestionId(questionId)
                || incorrectQuestionRepository.existsByQuestionId(questionId)
                || groupQuestionRepository.existsByQuestionId(questionId)) {
            throw new BusinessException(ErrorCode.QUESTION_IN_USE);
        }

        DetailedExplanationEntity explanation = question.getDetailExplanationId() == null ? null
                : explanationRepository.findForUpdate(question.getDetailExplanationId()).orElse(null);
        Set<String> retiredUrls = new HashSet<>();
        if (question.getImageUrl() != null) retiredUrls.add(question.getImageUrl());
        try {
            // Remove both kinds of metadata, including any legacy cross-type links.
            tagRepository.deleteAll(tagRepository.findAllByQuestionId(questionId));
            pastExamQuestionRepository.deleteAll(pastExamQuestionRepository.findAllByQuestionId(questionId));
            // Respect real database FKs: metadata first, question next, orphan explanation last.
            questionRepository.flush();
            questionRepository.delete(question);
            questionRepository.flush();
            if (explanation != null
                    && !questionRepository.existsByDetailExplanationId(explanation.getDetailExplanationId())) {
                if (explanation.getImageUrl() != null) retiredUrls.add(explanation.getImageUrl());
                explanationRepository.delete(explanation);
                explanationRepository.flush();
            }
        } catch (DataIntegrityViolationException exception) {
            // A concurrent/new FK reference must preserve all records and files through rollback.
            throw new BusinessException(ErrorCode.QUESTION_IN_USE, exception);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) return;
                for (String url : retiredUrls) {
                    try {
                        imageCleanup.deleteIfUnreferenced(url);
                    } catch (Exception exception) {
                        log.error("문제 삭제 커밋 후 이미지 정리 실패: {}", url, exception);
                    }
                }
            }
        });
        return ResponseDto.success(HttpStatus.OK);
    }
}
