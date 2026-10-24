package com.jhe.question_bank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jhe.question_bank.common.entity.DetailedExplanationEntity;

@Repository
public interface DetailedExplanationRepository extends JpaRepository<DetailedExplanationEntity, Integer> {
    

    boolean existsByImageUrl(String imageUrl);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT d FROM detailedExplanations d WHERE d.detailExplanationId = :id")
    java.util.Optional<DetailedExplanationEntity> findForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);
}
