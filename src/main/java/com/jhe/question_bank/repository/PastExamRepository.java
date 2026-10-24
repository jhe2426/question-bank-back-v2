package com.jhe.question_bank.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jhe.question_bank.common.entity.PastExamEntity;

@Repository
public interface PastExamRepository extends JpaRepository<PastExamEntity, Integer>{
    PastExamEntity findByPastExamId(Integer pastExamId);
    List<PastExamEntity> findAllByOrderByPastExamRoundAsc();
    Optional<PastExamEntity> findByPastExamRound(Integer pastExamRound);
}
