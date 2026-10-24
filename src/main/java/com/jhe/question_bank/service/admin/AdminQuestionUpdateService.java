package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.dto.request.admin.PutAdminQuestionRequestDto;
import com.jhe.question_bank.common.dto.response.ResponseDto;
import com.jhe.question_bank.common.entity.*;
import com.jhe.question_bank.common.exception.BusinessException;
import com.jhe.question_bank.common.exception.ErrorCode;
import com.jhe.question_bank.common.vo.StoredImage;
import com.jhe.question_bank.repository.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminQuestionUpdateService {
    private final QuestionRepository questionRepository;
    private final DetailedExplanationRepository explanationRepository;
    private final PastExamQuestionRepository pastExamQuestionRepository;
    private final MockPastTagRepository tagRepository;
    private final SectionRepository sectionRepository;
    private final AdminPastExamService pastExamService;
    private final AdminImageStorageService imageStorage;
    private final AdminImageCleanupService imageCleanup;
    private final Validator validator;

    // Serializable predicate reads also protect creation of a previously absent round/order.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<ResponseDto> updateQuestion(Integer questionId, PutAdminQuestionRequestDto dto,
                                                       MultipartFile questionImage, MultipartFile explanationImage) {
        QuestionEntity question = questionRepository.findForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_QUESTION_NOT_FOUND));
        validate(question, dto, questionImage, explanationImage);

        if ("기출문제".equals(question.getQuestionType())) {
            updatePastExam(questionId, dto);
        } else {
            synchronizeTags(questionId, dto.getTags());
        }

        List<String> newKeys = new ArrayList<>();
        Set<String> retiredUrls = new HashSet<>();
        registerCleanup(newKeys, retiredUrls);

        String questionUrl = replaceImage(question.getImageUrl(), questionImage, dto.isDeleteQuestionImage(),
                question.getQuestionType(), newKeys, retiredUrls);
        Integer oldExplanationId = question.getDetailExplanationId();
        DetailedExplanationEntity oldExplanation = oldExplanationId == null ? null
                : explanationRepository.findById(oldExplanationId).orElse(null);
        String explanationUrl = replaceImage(oldExplanation == null ? null : oldExplanation.getImageUrl(),
                explanationImage, dto.isDeleteExplanationImage(), null, newKeys, retiredUrls);
        String content = dto.getDetailedExplanation() == null ? null : dto.getDetailedExplanation().getContent();
        if (content != null && content.isBlank()) content = null;
        Integer explanationId = updateExplanation(oldExplanation, content, explanationUrl);
        question.update(dto, questionUrl, explanationId);
        questionRepository.flush();
        if (oldExplanationId != null && !Objects.equals(oldExplanationId, explanationId)
                && !questionRepository.existsByDetailExplanationId(oldExplanationId)) {
            explanationRepository.deleteById(oldExplanationId);
        }
        return ResponseDto.success(HttpStatus.OK);
    }

    private void validate(QuestionEntity question, PutAdminQuestionRequestDto dto,
                          MultipartFile questionImage, MultipartFile explanationImage) {
        if (dto == null || !validator.validate(dto).isEmpty()
                || !sectionRepository.existsById(dto.getSectionId())
                || dto.getTags() == null
                || new HashSet<>(dto.getTags()).size() != dto.getTags().size()
                || (questionImage != null && (questionImage.isEmpty() || dto.isDeleteQuestionImage()))
                || (explanationImage != null && (explanationImage.isEmpty() || dto.isDeleteExplanationImage()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAIL);
        }
        if ("기출문제".equals(question.getQuestionType())) {
            if (dto.getPastExamRound() == null || dto.getQuestionOrder() == null || !dto.getTags().isEmpty())
                throw new BusinessException(ErrorCode.VALIDATION_FAIL);
        } else if (!"모의고사".equals(question.getQuestionType())
                || dto.getDifficulty() == null
                || dto.getPastExamRound() != null || dto.getQuestionOrder() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAIL);
        }
    }

    private void updatePastExam(Integer questionId, PutAdminQuestionRequestDto dto) {
        List<PastExamQuestionEntity> links = pastExamQuestionRepository.findAllByQuestionId(questionId);
        if (links.size() > 1) throw new BusinessException(ErrorCode.VALIDATION_FAIL);
        Integer examId = pastExamService.getOrCreatePastExamId(dto.getPastExamRound());
        if (pastExamQuestionRepository.existsByPastExamIdAndQuestionOrderAndQuestionIdNot(
                examId, dto.getQuestionOrder(), questionId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAIL);
        }
        if (links.isEmpty()) {
            pastExamQuestionRepository.save(new PastExamQuestionEntity(questionId, examId, dto.getQuestionOrder()));
        } else {
            links.getFirst().update(examId, dto.getQuestionOrder());
        }
    }

    private void synchronizeTags(Integer questionId, List<Integer> requested) {
        Set<Integer> remaining = new HashSet<>(requested);
        for (MockPastTagEntity tag : tagRepository.findAllByQuestionId(questionId)) {
            if (!remaining.remove(tag.getPastExamRound())) tagRepository.delete(tag);
        }
        for (Integer round : remaining) tagRepository.save(new MockPastTagEntity(questionId, round));
    }

    private Integer updateExplanation(DetailedExplanationEntity old, String content, String imageUrl) {
        if (content == null && imageUrl == null) return null;
        if (old != null && Objects.equals(old.getContent(), content) && Objects.equals(old.getImageUrl(), imageUrl))
            return old.getDetailExplanationId();
        if (old != null && questionRepository.countByDetailExplanationId(old.getDetailExplanationId()) == 1) {
            old.update(content, imageUrl);
            return old.getDetailExplanationId();
        }
        return explanationRepository.save(new DetailedExplanationEntity(content, imageUrl)).getDetailExplanationId();
    }

    private String replaceImage(String oldUrl, MultipartFile file, boolean delete, String questionType,
                                List<String> newKeys, Set<String> retiredUrls) {
        String newUrl = oldUrl;
        if (file != null) {
            StoredImage image;
            try {
                image = questionType == null ? imageStorage.saveExplanation(file) : imageStorage.save(file, questionType);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_FAIL, exception);
            }
            newKeys.add(image.getStorageKey());
            newUrl = image.getUrl();
        } else if (delete) {
            newUrl = null;
        }
        if (oldUrl != null && !Objects.equals(oldUrl, newUrl)) retiredUrls.add(oldUrl);
        return newUrl;
    }

    private void registerCleanup(List<String> newKeys, Set<String> retiredUrls) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    for (String url : retiredUrls) {
                        try {
                            imageCleanup.deleteIfUnreferenced(url);
                        } catch (Exception exception) {
                            log.error("커밋 후 기존 이미지 정리 실패: {}", url, exception);
                        }
                    }
                } else if (status == STATUS_ROLLED_BACK) {
                    for (String key : newKeys) {
                        try {
                            imageStorage.delete(key);
                        } catch (Exception exception) {
                            log.error("수정 롤백 후 새 이미지 정리 실패: {}", key, exception);
                        }
                    }
                } else {
                    log.error("수정 트랜잭션 결과 불명: 새 이미지 삭제 보류 {}", newKeys);
                }
            }
        });
    }
}
