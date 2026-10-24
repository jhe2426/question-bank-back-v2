package com.jhe.question_bank.repository;


import com.jhe.question_bank.common.entity.MockPastTagEntity;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.jhe.question_bank.common.entity.primaryKey.MockPastTagPk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockPastTagRepository extends JpaRepository<MockPastTagEntity, MockPastTagPk> {
    @Query("SELECT t.pastExamRound FROM mockPastTags t WHERE t.questionId = :questionId ORDER BY t.pastExamRound ASC")
    List<Integer> findPastExamRoundsByQuestionId(@Param("questionId") Integer questionId);

    List<MockPastTagEntity> findAllByQuestionId(Integer questionId);
}
