package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.entity.*;
import com.jhe.question_bank.config.WebSecurityConfig;
import com.jhe.question_bank.controller.PastExamController;
import com.jhe.question_bank.controller.MockExamController;
import com.jhe.question_bank.service.implement.MockExamServiceImplement;
import com.jhe.question_bank.enums.Role;
import org.springframework.test.util.ReflectionTestUtils;
import com.jhe.question_bank.controller.admin.AdminQuestionController;
import com.jhe.question_bank.filter.JwtAuthenticationFilter;
import com.jhe.question_bank.handler.CustomExceptionHandler;
import com.jhe.question_bank.provider.JwtProvider;
import com.jhe.question_bank.repository.*;
import com.jhe.question_bank.service.implement.PastExamServiceImplement;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AdminQuestionQueryIntegrationTest.Config.class, properties = {
        "spring.config.location=optional:classpath:/admin-query-test.properties",
        "spring.datasource.url=jdbc:h2:mem:admin-query;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminQuestionQueryIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = QuestionEntity.class)
    @EnableJpaRepositories(basePackageClasses = QuestionRepository.class)
    @Import({AdminQuestionController.class, AdminQuestionQueryService.class, AdminQuestionService.class, AdminQuestionUpdateService.class,
            AdminPastExamService.class, AdminImageCleanupService.class,
            PastExamController.class, PastExamServiceImplement.class, MockExamController.class,
            MockExamServiceImplement.class, CustomExceptionHandler.class,
            WebSecurityConfig.class, JwtAuthenticationFilter.class})
    static class Config {}

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired QuestionRepository questions;
    @Autowired PastExamRepository exams;
    @Autowired PastExamQuestionRepository links;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean AdminImageStorageService imageStorage;
    @MockitoBean SectionRepository sections;
    private Integer round39Id;
    private Integer latestMockId;

    @BeforeEach
    void seed() {
        PastExamEntity round39 = exams.save(new PastExamEntity(39));
        round39Id = round39.getPastExamId();
        PastExamEntity round40 = exams.save(new PastExamEntity(40));
        for (int order = 25; order >= 1; order--) {
            QuestionEntity q = saveQuestion("기출문제", "중", "39회 문제 " + order);
            links.save(new PastExamQuestionEntity(q.getQuestionId(), round39Id, order));
        }
        QuestionEntity other = saveQuestion("기출문제", "중", "40회 문제");
        links.save(new PastExamQuestionEntity(other.getQuestionId(), round40.getPastExamId(), 1));
        for (int i = 1; i <= 25; i++) {
            latestMockId = saveQuestion("모의고사", "중", "모의 문제 " + i).getQuestionId();
        }
        saveQuestion("모의고사", "상", "상 문제");
        saveQuestion("모의고사", "하", "하 문제");
        em.flush();
        em.clear();
    }

    private QuestionEntity saveQuestion(String type, String difficulty, String text) {
        return questions.save(new QuestionEntity(null, null, 111, type, text,
                "/api/v1/file/" + type + "/example.png", "1", "2", "3", "4", 1,
                "해설", difficulty, "2026-10-24 12:00:00"));
    }

    @Test
    void pastExamFiltersByRoundAndPagesInQuestionOrderWithOnlyTwoQueries() throws Exception {
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "기출문제").param("pastExamRound", "39"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SU"))
                .andExpect(jsonPath("$.questions", hasSize(20)))
                .andExpect(jsonPath("$.questions[*].pastExamRound", everyItem(is(39))))
                .andExpect(jsonPath("$.questions[*].questionOrder", contains(
                        1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20)))
                .andExpect(jsonPath("$.questions[0].sectionId").value(111))
                .andExpect(jsonPath("$.questions[0].imageUrl").value("/api/v1/file/기출문제/example.png"))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.last").value(false));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "기출문제").param("pastExamRound", "39").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[*].questionOrder", contains(21,22,23,24,25)))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void mockFiltersDifficultyAndSortsByDescendingIdAcrossPages() throws Exception {
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "모의고사").param("difficulty", "중"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(20)))
                .andExpect(jsonPath("$.questions[*].difficulty", everyItem(is("중"))))
                .andExpect(jsonPath("$.questions[*].questionType", everyItem(is("모의고사"))))
                .andExpect(jsonPath("$.questions[0].questionId").value(latestMockId))
                .andExpect(jsonPath("$.questions[19].questionId").value(latestMockId - 19))
                .andExpect(jsonPath("$.questions[0].pastExamRound").value(nullValue()))
                .andExpect(jsonPath("$.questions[0].questionOrder").value(nullValue()))
                .andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "모의고사").param("difficulty", "중").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(5)))
                .andExpect(jsonPath("$.questions[0].questionId").value(latestMockId - 20))
                .andExpect(jsonPath("$.last").value(true)).andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void absentRoundReturnsEmptySuccessfulPage() throws Exception {
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "기출문제").param("pastExamRound", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @CsvSource({"기출문제,,중,0,20", "기출문제,0,중,0,20", "기출문제,-1,중,0,20",
            "기출문제,abc,중,0,20", "기출문제,1.5,중,0,20", "모의고사,,,0,20",
            "모의고사,,최상,0,20", "기타,39,중,0,20", ",39,중,0,20",
            "모의고사,,중,-1,20", "모의고사,,중,0,0", "모의고사,,중,0,101",
            "모의고사,,중,abc,20", "모의고사,,중,0,abc"})
    void invalidConditionsReturn400(String type, String round, String difficulty, String page, String size) throws Exception {
        var request = get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                .param("page", page).param("size", size);
        if (type != null) request.param("questionType", type);
        if (round != null) request.param("pastExamRound", round);
        if (difficulty != null) request.param("difficulty", difficulty);
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VF"));
    }

    @Test
    void maximumSizeAndOutOfRangePage() throws Exception {
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "모의고사").param("difficulty", "중").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(25)))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "모의고사").param("difficulty", "중").param("page", "9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void anonymousAndRegularUsersCannotReadAdminQuestions() throws Exception {
        mvc.perform(get("/api/admin/questions").param("questionType", "모의고사").param("difficulty", "중"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/questions").with(user("user").roles("USER"))
                        .param("questionType", "모의고사").param("difficulty", "중"))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingUserPastExamEndpointStillUsesInternalId() throws Exception {
        mvc.perform(get("/api/v1/past-exam/{id}", round39Id).with(user("user").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SU"))
                .andExpect(jsonPath("$.questions", hasSize(25)))
                .andExpect(jsonPath("$.questions[0].questionOrder").value(1));
    }

    @Test
    void existingBulkRegistrationCanBeReadThroughNewEndpoint() throws Exception {
        when(sections.existsById(111)).thenReturn(true);
        String json = """
                {"questions":[{"sectionId":111,"questionType":"기출문제","difficulty":"중",
                "questionText":"새 기출문제","option1":"1","option2":"2","option3":"3","option4":"4",
                "answer":1,"pastExamRound":41,"questionOrder":1}]}
                """;
        mvc.perform(multipart("/api/admin/questions").file(new MockMultipartFile(
                        "jsonFile", "questions.json", "application/json", json.getBytes(StandardCharsets.UTF_8)))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("SU"));
        mvc.perform(get("/api/admin/questions").with(user("admin").roles("ADMIN"))
                        .param("questionType", "기출문제").param("pastExamRound", "41"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].questionText").value("새 기출문제"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void existingMockExamEndpointStillSelectsTenQuestions() throws Exception {
        UserEntity user = new UserEntity();
        ReflectionTestUtils.setField(user, "userId", "student");
        ReflectionTestUtils.setField(user, "currentMockExamRound", 1);
        ReflectionTestUtils.setField(user, "role", Role.USER);
        em.persist(user);
        SectionEntity section = org.springframework.beans.BeanUtils.instantiateClass(SectionEntity.class);
        ReflectionTestUtils.setField(section, "sectionId", 111);
        ReflectionTestUtils.setField(section, "chapterId", 1);
        em.merge(section);
        em.flush();
        when(jwtProvider.validateAccessToken("test-student-token")).thenReturn("student");
        mvc.perform(get("/api/v1/mock-exam/1/MEDIUM/false").header("Authorization", "Bearer test-student-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(10)))
                .andExpect(jsonPath("$.sessionId").value(1));
    }

    @Test
    void jwtFilterUsesDatabaseAdminRole() throws Exception {
        UserEntity admin = new UserEntity();
        ReflectionTestUtils.setField(admin, "userId", "jwt-admin");
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        em.persist(admin);
        em.flush();
        when(jwtProvider.validateAccessToken("test-admin-token")).thenReturn("jwt-admin");
        mvc.perform(get("/api/admin/questions").header("Authorization", "Bearer test-admin-token")
                        .param("questionType", "모의고사").param("difficulty", "중"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void pastExamDetailReturnsAllFieldsAndOriginalImageUrls() throws Exception {
        DetailedExplanationEntity explanation = new DetailedExplanationEntity(
                "상세 해설 내용", "/api/v1/file/해설/original.png");
        em.persist(explanation);
        QuestionEntity question = questions.save(new QuestionEntity(null, explanation.getDetailExplanationId(),
                111, "기출문제", "상세 문제 지문", "/api/v1/file/기출문제/original.png",
                "선택지1", "선택지2", "선택지3", "선택지4", 3, "간단 해설", "중", "2026-10-24 12:00:00"));
        links.save(new PastExamQuestionEntity(question.getQuestionId(), round39Id, 26));
        em.flush();
        em.clear();
        mvc.perform(get("/api/admin/questions/{id}", question.getQuestionId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SU"))
                .andExpect(jsonPath("$.message").value("Success."))
                .andExpect(jsonPath("$.questionId").value(question.getQuestionId()))
                .andExpect(jsonPath("$.questionType").value("기출문제"))
                .andExpect(jsonPath("$.sectionId").value(111)).andExpect(jsonPath("$.difficulty").value("중"))
                .andExpect(jsonPath("$.questionText").value("상세 문제 지문"))
                .andExpect(jsonPath("$.imageUrl").value("/api/v1/file/기출문제/original.png"))
                .andExpect(jsonPath("$.option1").value("선택지1")).andExpect(jsonPath("$.option2").value("선택지2"))
                .andExpect(jsonPath("$.option3").value("선택지3")).andExpect(jsonPath("$.option4").value("선택지4"))
                .andExpect(jsonPath("$.answer").value(3)).andExpect(jsonPath("$.shortExplanation").value("간단 해설"))
                .andExpect(jsonPath("$.detailedExplanation.content").value("상세 해설 내용"))
                .andExpect(jsonPath("$.detailedExplanation.imageUrl").value("/api/v1/file/해설/original.png"))
                .andExpect(jsonPath("$.pastExamRound").value(39)).andExpect(jsonPath("$.questionOrder").value(26))
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void mockDetailReturnsOnlyItsOwnTagsInAscendingOrder() throws Exception {
        em.persist(new MockPastTagEntity(latestMockId, 39));
        em.persist(new MockPastTagEntity(latestMockId, 35));
        em.persist(new MockPastTagEntity(latestMockId - 1, 40));
        em.flush();
        em.clear();
        mvc.perform(get("/api/admin/questions/{id}", latestMockId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questionType").value("모의고사"))
                .andExpect(jsonPath("$.tags", contains(35, 39)))
                .andExpect(jsonPath("$.pastExamRound").value(nullValue()))
                .andExpect(jsonPath("$.questionOrder").value(nullValue()))
                .andExpect(jsonPath("$.detailedExplanation").value(nullValue()));
    }

    @Test
    void detailWithoutOptionalDataReturnsNullsAndEmptyTags() throws Exception {
        QuestionEntity question = questions.save(new QuestionEntity(null, null, 111, "모의고사", "이미지 없는 문제",
                null, "1", "2", "3", "4", 1, null, "하", "2026-10-24 12:00:00"));
        mvc.perform(get("/api/admin/questions/{id}", question.getQuestionId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.shortExplanation").value(nullValue()))
                .andExpect(jsonPath("$.detailedExplanation").value(nullValue()))
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void imageOnlyDetailedExplanationPreservesNullContent() throws Exception {
        DetailedExplanationEntity explanation = new DetailedExplanationEntity(null, "/original/explanation.png");
        em.persist(explanation);
        QuestionEntity question = questions.save(new QuestionEntity(null, explanation.getDetailExplanationId(),
                111, "모의고사", "이미지 해설 문제", null, "1", "2", "3", "4", 1, null, "중", null));
        mvc.perform(get("/api/admin/questions/{id}", question.getQuestionId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detailedExplanation.content").value(nullValue()))
                .andExpect(jsonPath("$.detailedExplanation.imageUrl").value("/original/explanation.png"));
    }

    @Test
    void nonexistentQuestionReturns404InResponseDtoFormat() throws Exception {
        mvc.perform(get("/api/admin/questions/2147483647").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NQI"))
                .andExpect(jsonPath("$.message").value("Question ID does not exist."));
    }

    @Test
    void nonIntegerQuestionIdReturns400() throws Exception {
        mvc.perform(get("/api/admin/questions/not-a-number").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VF"));
    }

    @Test
    void detailRequiresAdminRole() throws Exception {
        mvc.perform(get("/api/admin/questions/{id}", latestMockId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/questions/{id}", latestMockId).with(user("student").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
