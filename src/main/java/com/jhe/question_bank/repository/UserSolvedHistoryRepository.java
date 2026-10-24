package com.jhe.question_bank.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jhe.question_bank.common.entity.UserSolvedHistoryEntity;
import com.jhe.question_bank.repository.resultSet.AdminSectionLearningSummaryResultSet;

public interface UserSolvedHistoryRepository extends JpaRepository<UserSolvedHistoryEntity, Integer> {
    int countByUserIdAndSessionIdAndSourceType(String userId, Integer sessionId, String sourceType);

    @Query("SELECT USH.questionId FROM userSolvedHistory USH WHERE USH.userId = :userId AND USH.sessionId = :sessionId AND USH.sourceType = :sourceType")
    List<Integer> findSolvedQuestionIdsBySessionId(@Param("userId") String userId, @Param("sessionId") Integer sessionId, @Param("sourceType") String sourceType);

    boolean existsByQuestionId(Integer questionId);

    @Query("""
            SELECT COUNT(DISTINCT ush.questionId)
            FROM userSolvedHistory ush
            WHERE ush.userId = :userId
            """)
    long countDistinctSolvedQuestionsByUserId(@Param("userId") String userId);

    @Query("""
            SELECT q.sectionId AS sectionId,
                   s.sectionName AS sectionName,
                   COUNT(DISTINCT ush.questionId) AS solvedCount
            FROM userSolvedHistory ush
            JOIN questions q ON q.questionId = ush.questionId
            JOIN sections s ON s.sectionId = q.sectionId
            WHERE ush.userId = :userId
            GROUP BY q.sectionId, s.sectionName
            ORDER BY q.sectionId ASC
            """)
    List<AdminSectionLearningSummaryResultSet> findSectionLearningSummaries(@Param("userId") String userId);
}
