package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.entity.QuestionEntity;
import com.jhe.question_bank.common.entity.SectionEntity;
import com.jhe.question_bank.common.entity.UserEntity;
import com.jhe.question_bank.common.entity.UserProblemGroupEntity;
import com.jhe.question_bank.common.entity.UserSolvedHistoryEntity;
import com.jhe.question_bank.config.WebSecurityConfig;
import com.jhe.question_bank.controller.admin.AdminUserController;
import com.jhe.question_bank.enums.Role;
import com.jhe.question_bank.filter.JwtAuthenticationFilter;
import com.jhe.question_bank.handler.CustomExceptionHandler;
import com.jhe.question_bank.provider.JwtProvider;
import com.jhe.question_bank.repository.QuestionRepository;
import com.jhe.question_bank.repository.UserProblemGroupRepository;
import com.jhe.question_bank.repository.UserRepository;
import com.jhe.question_bank.repository.UserSolvedHistoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AdminUserQueryIntegrationTest.Config.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-user-query;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminUserQueryIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserEntity.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({AdminUserController.class, AdminUserQueryService.class, CustomExceptionHandler.class,
            WebSecurityConfig.class, JwtAuthenticationFilter.class})
    static class Config {}

    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired UserSolvedHistoryRepository solvedHistories;
    @Autowired UserProblemGroupRepository problemGroups;
    @MockitoBean JwtProvider jwtProvider;

    private Integer firstSectionQuestionId;
    private Integer secondQuestionInFirstSectionId;
    private Integer secondSectionQuestionId;

    @BeforeEach
    void seed() {
        users.save(userEntity("student", "학습자", "01012345678", Role.USER));
        users.save(userEntity("empty-student", "신규회원", "01000000000", Role.USER));
        users.save(userEntity("other-student", "다른회원", "01099999999", Role.USER));
        users.save(userEntity("admin-account", "관리자", "01011111111", Role.ADMIN));

        entityManager.persist(section(111, "데이터의 이해"));
        entityManager.persist(section(112, "데이터 분석 기획"));

        firstSectionQuestionId = questions.save(question(111, "첫 번째 문제")).getQuestionId();
        secondQuestionInFirstSectionId = questions.save(question(111, "두 번째 문제")).getQuestionId();
        secondSectionQuestionId = questions.save(question(112, "세 번째 문제")).getQuestionId();

        solvedHistories.save(history("student", firstSectionQuestionId, true));
        solvedHistories.save(history("student", firstSectionQuestionId, false));
        solvedHistories.save(history("student", firstSectionQuestionId, true));
        solvedHistories.save(history("student", secondQuestionInFirstSectionId, false));
        solvedHistories.save(history("student", secondSectionQuestionId, true));

        // 다른 회원 데이터가 대상 회원의 집계에 섞이지 않는지도 함께 검증한다.
        solvedHistories.save(history("other-student", firstSectionQuestionId, true));
        problemGroups.save(group("student", 120));
        problemGroups.save(group("student", 230));
        problemGroups.save(group("other-student", 999));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void sectionSummaryCountsEveryAttemptAndSumsEachProblemGroupOnce() throws Exception {
        mvc.perform(get("/api/admin/users/{userId}/learning-summary", "student")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SU"))
                .andExpect(jsonPath("$.userId").value("student"))
                .andExpect(jsonPath("$.userName").value("학습자"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.totalSolvedCount").value(3))
                // 풀이 이력 5행과 JOIN되어 5배가 되지 않고 120 + 230만 반환해야 한다.
                .andExpect(jsonPath("$.totalSolveSeconds").value(350))
                .andExpect(jsonPath("$.sectionSummaries", hasSize(2)))
                .andExpect(jsonPath("$.sectionSummaries[0].sectionId").value(111))
                .andExpect(jsonPath("$.sectionSummaries[0].sectionName").value("데이터의 이해"))
                .andExpect(jsonPath("$.sectionSummaries[0].solvedCount").value(4))
                .andExpect(jsonPath("$.sectionSummaries[0].correctCount").value(2))
                .andExpect(jsonPath("$.sectionSummaries[0].accuracyRate").value(closeTo(50.0, 0.0001)))
                .andExpect(jsonPath("$.sectionSummaries[0].totalSolveSeconds").doesNotExist())
                .andExpect(jsonPath("$.sectionSummaries[1].sectionId").value(112))
                .andExpect(jsonPath("$.sectionSummaries[1].solvedCount").value(1))
                .andExpect(jsonPath("$.sectionSummaries[1].correctCount").value(1))
                .andExpect(jsonPath("$.sectionSummaries[1].accuracyRate").value(closeTo(100.0, 0.0001)));
    }

    @Test
    void learningSummaryKeepsNullTimePolicyWhenNoTimeWasRecorded() throws Exception {
        mvc.perform(get("/api/admin/users/{userId}/learning-summary", "empty-student")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSolvedCount").value(0))
                .andExpect(jsonPath("$.totalSolveSeconds").value(nullValue()))
                .andExpect(jsonPath("$.sectionSummaries", hasSize(0)));
    }

    @Test
    void learningSummaryRetainsAdminAuthorizationAndUserRoleCheck() throws Exception {
        mvc.perform(get("/api/admin/users/student/learning-summary"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/student/learning-summary")
                        .with(user("student").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/admin-account/learning-summary")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void existingAdminUserListRemainsAvailable() throws Exception {
        mvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.users[?(@.userId == 'admin-account')]").isEmpty());
    }

    private UserEntity userEntity(String id, String name, String phoneNumber, Role role) {
        return new UserEntity(id, "password", null, 1, 1, phoneNumber, name,
                "2000-01-01", "서울", null, null, "2026-10-25 12:00:00", role);
    }

    private SectionEntity section(int id, String name) {
        SectionEntity section = org.springframework.beans.BeanUtils.instantiateClass(SectionEntity.class);
        ReflectionTestUtils.setField(section, "sectionId", id);
        ReflectionTestUtils.setField(section, "chapterId", 1);
        ReflectionTestUtils.setField(section, "sectionName", name);
        return section;
    }

    private QuestionEntity question(int sectionId, String text) {
        return new QuestionEntity(null, null, sectionId, "모의고사", text, null,
                "1", "2", "3", "4", 1, "해설", "중", "2026-10-25 12:00:00");
    }

    private UserSolvedHistoryEntity history(String userId, int questionId, boolean correct) {
        return new UserSolvedHistoryEntity(null, userId, questionId, 1, correct ? 1 : 2,
                correct, "모의고사", "2026-10-25 12:00:00");
    }

    private UserProblemGroupEntity group(String userId, Integer seconds) {
        return new UserProblemGroupEntity(null, userId, "모의고사", "모의고사",
                null, 0, "2026-10-25 12:00:00", seconds);
    }
}
