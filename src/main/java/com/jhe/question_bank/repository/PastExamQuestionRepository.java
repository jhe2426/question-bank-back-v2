package com.jhe.question_bank.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.jhe.question_bank.common.dto.response.admin.AdminQuestionListItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jhe.question_bank.common.entity.PastExamQuestionEntity;

@Repository
public interface  PastExamQuestionRepository extends JpaRepository<PastExamQuestionEntity, Integer> {

    List<PastExamQuestionEntity> findByPastExamIdOrderByQuestionOrderAsc(Integer pastExamId);
    PastExamQuestionEntity findByQuestionIdAndPastExamId(Integer questionId, Integer pastExamId);


    @Query(value = """
            SELECT new com.jhe.question_bank.common.dto.response.admin.AdminQuestionListItemDto(
                q.questionId, q.questionType, q.questionText, q.difficulty, q.imageUrl, q.sectionId,
                pe.pastExamRound, pq.questionOrder)
            FROM pastExamQuestions pq
            JOIN pastExams pe ON pe.pastExamId = pq.pastExamId
            JOIN questions q ON q.questionId = pq.questionId
            WHERE pe.pastExamRound = :round AND q.questionType = '기출문제'
            ORDER BY pq.questionOrder ASC, pq.pastExamQuestionId ASC
            """, countQuery = """
            SELECT COUNT(pq) FROM pastExamQuestions pq
            JOIN pastExams pe ON pe.pastExamId = pq.pastExamId
            JOIN questions q ON q.questionId = pq.questionId
            WHERE pe.pastExamRound = :round AND q.questionType = '기출문제'
            """)
    Page<AdminQuestionListItemDto> findAdminQuestionsByRound(@Param("round") Integer round, Pageable pageable);
}
