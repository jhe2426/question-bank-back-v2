package com.jhe.question_bank.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jhe.question_bank.common.entity.PastExamEntity;

@Repository
public interface PastExamRepository extends JpaRepository<PastExamEntity, Integer>{
    PastExamEntity findByPastExamId(Integer pastExamId);
    List<PastExamEntity> findAllByOrderByPastExamRoundAsc();
    Optional<PastExamEntity> findByPastExamRound(Integer pastExamRound);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM pastExams p WHERE p.pastExamRound = :round")
    Optional<PastExamEntity> findForUpdateByRound(@Param("round") Integer round);
}
