package com.jhe.question_bank.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jhe.question_bank.common.entity.UserProblemGroupEntity;

@Repository
public interface UserProblemGroupRepository extends JpaRepository<UserProblemGroupEntity, Integer>{
    UserProblemGroupEntity findByGroupId(Integer groupId);
    List<UserProblemGroupEntity> findByUserIdOrderBySolvedAtDesc(String userId);

    @Query("SELECT SUM(g.timeSpentSeconds) FROM userProblemGroups g WHERE g.userId = :userId")
    Long sumTimeSpentSecondsByUserId(@Param("userId") String userId);
}
