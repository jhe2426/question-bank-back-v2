package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.entity.*;
import com.jhe.question_bank.provider.JwtProvider;
import com.jhe.question_bank.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {AdminQuestionQueryIntegrationTest.Config.class, AdminImageStorageService.class}, properties = {
        "spring.config.location=optional:classpath:/admin-delete-test.properties",
        "spring.datasource.url=jdbc:h2:mem:admin-delete;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "file.public-base-url=https://localhost:4000", "mock-question.folder-name=mockExamQuestion",
        "past-question.folder-name=pastExamQuestion", "explanation.folder-name=explanation"
})
@AutoConfigureMockMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminQuestionDeleteIntegrationTest {
    static final Path imageRoot = Path.of(System.getProperty("java.io.tmpdir"), "admin-question-delete-" + java.util.UUID.randomUUID());
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("file.path", () -> imageRoot.toString()); }
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AdminImageStorageService storage;
    @Autowired QuestionRepository questions;
    @Autowired PastExamRepository exams;
    @Autowired PastExamQuestionRepository links;
    @Autowired MockPastTagRepository tags;
    @Autowired DetailedExplanationRepository explanations;
    @Autowired UserSolvedHistoryRepository solved;
    @Autowired UserIncorrectQuestionRepository incorrect;
    @Autowired GroupQuestionRepository groups;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean SectionRepository sections;
    Integer questionId;
    Integer explanationId;
    Integer examId;
    String questionUrl;
    String explanationUrl;

    @BeforeAll
    void installAuditedForeignKeysInTestDatabase() {
        for (String table : new String[]{"mock_past_tags", "past_exam_questions", "user_solved_history", "user_incorrect_questions", "group_questions"}) {
            jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT delete_test_" + table
                    + " FOREIGN KEY (question_id) REFERENCES questions(question_id) ON DELETE NO ACTION");
        }
        jdbc.execute("ALTER TABLE questions ADD CONSTRAINT delete_test_explanation FOREIGN KEY (detail_explanation_id) REFERENCES detailed_explanations(detail_explanation_id) ON DELETE NO ACTION");
        jdbc.execute("ALTER TABLE past_exam_questions ADD CONSTRAINT delete_test_exam FOREIGN KEY (past_exam_id) REFERENCES past_exams(past_exam_id) ON DELETE NO ACTION");
        jdbc.execute("CREATE TABLE delete_test_other_reference (question_id INT REFERENCES questions(question_id))");
        jdbc.execute("CREATE TABLE delete_test_other_explanation (detail_explanation_id INT REFERENCES detailed_explanations(detail_explanation_id))");
    }

    @BeforeEach
    void seed() {
        MockMultipartFile image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10});
        questionUrl = storage.save(image, "기출문제").getUrl();
        explanationUrl = storage.saveExplanation(image).getUrl();
        explanationId = explanations.save(new DetailedExplanationEntity("상세 해설", explanationUrl)).getDetailExplanationId();
        questionId = newQuestion("기출문제", explanationId, questionUrl).getQuestionId();
        examId = exams.save(new PastExamEntity(39)).getPastExamId();
        links.save(new PastExamQuestionEntity(questionId, examId, 1));
        em.flush();
    }

    QuestionEntity newQuestion(String type, Integer explanation, String url) {
        return questions.save(new QuestionEntity(null, explanation, 111, type, "삭제 대상", url,
                "1", "2", "3", "4", 1, "간단 해설", "중", "2026-10-24 12:00:00"));
    }
    Path path(String url) { return imageRoot.resolve(storage.storageKeyFromUrl(url).orElseThrow()); }
    void commit() { TestTransaction.flagForCommit(); TestTransaction.end(); }

    @AfterEach
    void clean() {
        if (TestTransaction.isActive()) { TestTransaction.flagForRollback(); TestTransaction.end(); }
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM delete_test_other_reference");
            jdbc.update("DELETE FROM delete_test_other_explanation");
            solved.deleteAllInBatch(); incorrect.deleteAllInBatch(); groups.deleteAllInBatch();
            tags.deleteAllInBatch(); links.deleteAllInBatch(); questions.deleteAllInBatch();
            explanations.deleteAllInBatch(); exams.deleteAllInBatch();
        });
    }

    @Test
    void deletesPastQuestionAndOrphanExplanationButKeepsRoundAndFilesUntilCommit() throws Exception {
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SU"))
                .andExpect(jsonPath("$.message").value("Success."));
        assertThat(questions.existsById(questionId)).isFalse();
        assertThat(links.findAllByQuestionId(questionId)).isEmpty();
        assertThat(explanations.existsById(explanationId)).isFalse();
        assertThat(exams.existsById(examId)).isTrue();
        assertThat(path(questionUrl)).exists(); assertThat(path(explanationUrl)).exists();
        mvc.perform(get("/api/admin/questions").param("questionType", "기출문제").param("pastExamRound", "39")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
        commit();
        assertThat(path(questionUrl)).doesNotExist(); assertThat(path(explanationUrl)).doesNotExist();
        assertThat(exams.existsById(examId)).isTrue();
    }

    @Test
    void deletesMockQuestionAndTagsThroughCommittedRequest() throws Exception {
        var mock = newQuestion("모의고사", null, null);
        tags.save(new MockPastTagEntity(mock.getQuestionId(), 35));
        tags.save(new MockPastTagEntity(mock.getQuestionId(), 39));
        commit();
        mvc.perform(delete("/api/admin/questions/{id}", mock.getQuestionId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        assertThat(questions.existsById(mock.getQuestionId())).isFalse();
        assertThat(tags.findAllByQuestionId(mock.getQuestionId())).isEmpty();
        mvc.perform(get("/api/admin/questions").param("questionType", "모의고사").param("difficulty", "중")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions", hasSize(0)));
        mvc.perform(get("/api/admin/questions/{id}", mock.getQuestionId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void retainsSharedExplanationAndImages() throws Exception {
        var other = newQuestion("모의고사", explanationId, questionUrl);
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        commit();
        assertThat(questions.existsById(other.getQuestionId())).isTrue();
        assertThat(explanations.existsById(explanationId)).isTrue();
        assertThat(path(questionUrl)).exists(); assertThat(path(explanationUrl)).exists();
    }

    @Test
    void preservesFileSharedBySeparateExplanationRows() throws Exception {
        var otherExplanation = explanations.save(new DetailedExplanationEntity("다른 해설", explanationUrl));
        newQuestion("모의고사", otherExplanation.getDetailExplanationId(), null);
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        commit();
        assertThat(explanations.existsById(explanationId)).isFalse();
        assertThat(path(explanationUrl)).exists(); assertThat(path(questionUrl)).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"solved", "incorrect", "group"})
    void protectsEveryLearningReferenceWith409(String reference) throws Exception {
        switch (reference) {
            case "solved" -> solved.save(new UserSolvedHistoryEntity(null, "student", questionId, 1, 1, true, "기출문제", null));
            case "incorrect" -> incorrect.save(new UserIncorrectQuestionEntity("student", questionId, "기출문제"));
            case "group" -> groups.save(new GroupQuestionEntity(null, null, questionId, 1, true, 1));
        }
        commit();
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QIU"));
        assertThat(questions.existsById(questionId)).isTrue();
        assertThat(links.findAllByQuestionId(questionId)).hasSize(1);
        assertThat(explanations.existsById(explanationId)).isTrue();
        assertThat(solved.existsByQuestionId(questionId) || incorrect.existsByQuestionId(questionId)
                || groups.existsByQuestionId(questionId)).isTrue();
        assertThat(path(questionUrl)).exists(); assertThat(path(explanationUrl)).exists();
    }

    @Test
    void unknownQuestionForeignKeyRollsBackAlreadyDeletedMetadata() throws Exception {
        tags.save(new MockPastTagEntity(questionId, 39));
        jdbc.update("INSERT INTO delete_test_other_reference VALUES (?)", questionId);
        commit();
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());
        assertThat(questions.existsById(questionId)).isTrue();
        assertThat(tags.findAllByQuestionId(questionId)).hasSize(1);
        assertThat(links.findAllByQuestionId(questionId)).hasSize(1);
        assertThat(path(questionUrl)).exists(); assertThat(path(explanationUrl)).exists();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delete_test_other_reference", Integer.class)).isEqualTo(1);
    }

    @Test
    void explanationForeignKeyFailureRollsBackQuestionAndLinks() throws Exception {
        jdbc.update("INSERT INTO delete_test_other_explanation VALUES (?)", explanationId);
        commit();
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());
        assertThat(questions.existsById(questionId)).isTrue();
        assertThat(links.findAllByQuestionId(questionId)).hasSize(1);
        assertThat(explanations.existsById(explanationId)).isTrue();
        assertThat(path(questionUrl)).exists(); assertThat(path(explanationUrl)).exists();
    }

    @Test
    void laterTransactionRollbackNeverDeletesFiles() throws Exception {
        commit(); TestTransaction.start();
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        TestTransaction.flagForRollback(); TestTransaction.end();
        assertThat(questions.existsById(questionId)).isTrue();
        assertThat(links.findAllByQuestionId(questionId)).hasSize(1);
        assertThat(explanations.existsById(explanationId)).isTrue();
        assertThat(path(questionUrl)).exists(); assertThat(path(explanationUrl)).exists();
    }

    @Test
    void missingQuestionIs404AndOnlyAdminsCanDelete() throws Exception {
        mvc.perform(delete("/api/admin/questions/2147483647").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NQI"));
        mvc.perform(delete("/api/admin/questions/{id}", questionId)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/questions/{id}", questionId).with(user("student").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void storageRejectsTraversalAndSymlinkedExternalDirectory(@TempDir Path outside) throws Exception {
        Path external = Files.writeString(outside.resolve("protected.png"), "keep");
        Path symlink = imageRoot.resolve("external-link");
        Files.createSymbolicLink(symlink, outside);
        try {
            assertThatThrownBy(() -> storage.delete("../protected.png")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete("external-link/protected.png")).isInstanceOf(IllegalArgumentException.class);
            assertThat(external).hasContent("keep");
        } finally { Files.deleteIfExists(symlink); }
    }
}
