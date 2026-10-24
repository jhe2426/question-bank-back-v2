package com.jhe.question_bank.repository;

import com.jhe.question_bank.common.dto.response.admin.AdminUserListItemDto;
import com.jhe.question_bank.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jhe.question_bank.common.entity.UserEntity;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    
    boolean existsByUserId(String userId);
    boolean existsByPhoneNumber(String phoneNumber);

    UserEntity findByUserId(String userId);

    Optional<UserEntity> findByUserIdAndRole(String userId, Role role);

    @Query(value = """
            SELECT new com.jhe.question_bank.common.dto.response.admin.AdminUserListItemDto(
                u.userId, u.name, u.phoneNumber)
            FROM users u
            WHERE u.role = :role
              AND (
                :keyword IS NULL
                OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR (:phoneKeyword IS NOT NULL AND u.phoneNumber LIKE CONCAT('%', :phoneKeyword, '%'))
              )
            ORDER BY u.registeredAt DESC, u.userId ASC
            """, countQuery = """
            SELECT COUNT(u)
            FROM users u
            WHERE u.role = :role
              AND (
                :keyword IS NULL
                OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR (:phoneKeyword IS NOT NULL AND u.phoneNumber LIKE CONCAT('%', :phoneKeyword, '%'))
              )
            """)
    Page<AdminUserListItemDto> findAdminUserPage(
            @Param("role") Role role,
            @Param("keyword") String keyword,
            @Param("phoneKeyword") String phoneKeyword,
            Pageable pageable
    );
}
