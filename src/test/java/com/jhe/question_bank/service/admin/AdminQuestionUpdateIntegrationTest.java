package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.entity.*;
import com.jhe.question_bank.provider.JwtProvider;
import com.jhe.question_bank.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {AdminQuestionQueryIntegrationTest.Config.class, AdminImageStorageService.class,
        com.jhe.question_bank.controller.FileController.class,
        com.jhe.question_bank.service.implement.FileServiceImplement.class}, properties = {
        "spring.config.location=optional:classpath:/admin-update-test.properties",
        "spring.datasource.url=jdbc:h2:mem:admin-update;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "file.public-base-url=https://localhost:4000", "mock-question.folder-name=mockExamQuestion",
        "past-question.folder-name=pastExamQuestion", "explanation.folder-name=explanation"
})
@AutoConfigureMockMvc
@Transactional
class AdminQuestionUpdateIntegrationTest {
    @TempDir static Path imageRoot;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("file.path", () -> imageRoot.toString()); }
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired ObjectMapper mapper;
    @Autowired AdminImageStorageService storage;
    @Autowired PlatformTransactionManager transactions;
    @Autowired QuestionRepository questions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PastExamQuestionRepository links;
    @Autowired PastExamRepository exams;
    @Autowired MockPastTagRepository tags;
    @Autowired DetailedExplanationRepository explanations;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean SectionRepository sections;
    Integer questionId;
    Integer explanationId;
    Integer linkId;
    String oldQuestionUrl;
    String oldExplanationUrl;

    static MockMultipartFile image(String part) {
        return new MockMultipartFile(part, "sample.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10});
    }

    @BeforeEach
    void seed() {
        when(sections.existsById(111)).thenReturn(true);
        when(sections.existsById(222)).thenReturn(true);
        oldQuestionUrl = storage.save(image("questionImage"), "기출문제").getUrl();
        oldExplanationUrl = storage.saveExplanation(image("explanationImage")).getUrl();
        var explanation = explanations.save(new DetailedExplanationEntity("기존 상세", oldExplanationUrl));
        explanationId = explanation.getDetailExplanationId();
        var question = questions.save(new QuestionEntity(null, explanationId, 111, "기출문제", "기존 지문", oldQuestionUrl,
                "1", "2", "3", "4", 1, "기존 간단", "하", "2026-10-24 12:00:00"));
        questionId = question.getQuestionId();
        var exam = exams.save(new PastExamEntity(39));
        linkId = links.save(new PastExamQuestionEntity(questionId, exam.getPastExamId(), 1)).getPastExamQuestionId();
        em.flush();
    }

    @AfterEach
    void cleanDatabase() {
        if (TestTransaction.isActive()) { TestTransaction.flagForRollback(); TestTransaction.end(); }
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            tags.deleteAllInBatch(); links.deleteAllInBatch(); questions.deleteAllInBatch();
            explanations.deleteAllInBatch(); exams.deleteAllInBatch();
        });
    }

    Map<String,Object> body() {
        Map<String,Object> body = new HashMap<>();
        body.put("questionText", "수정 지문"); body.put("option1", "수정1"); body.put("option2", "수정2");
        body.put("option3", "수정3"); body.put("option4", "수정4"); body.put("answer", 4);
        body.put("difficulty", "상"); body.put("sectionId", 222); body.put("shortExplanation", "수정 간단");
        body.put("detailedExplanation", Map.of("content", "수정 상세"));
        body.put("pastExamRound", 39); body.put("questionOrder", 1); body.put("tags", List.of());
        return body;
    }

    MockMultipartHttpServletRequestBuilder request(Integer id, Map<String,Object> body) {
        return multipart(HttpMethod.PUT, "/api/admin/questions/{id}", id)
                .file(new MockMultipartFile("question", "question.json", "application/json", mapper.writeValueAsBytes(body)));
    }
    Path path(String url) { return imageRoot.resolve(storage.storageKeyFromUrl(url).orElseThrow()); }
    void commit() { TestTransaction.flagForCommit(); TestTransaction.end(); }
    void persistSeed() { commit(); }

    @Test
    void updatesAllTextFieldsAndUserApiSeesChangesWhileImagesRemain() throws Exception {
        mvc.perform(request(questionId, body()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SU"));
        em.flush(); em.clear();
        var question = questions.findById(questionId).orElseThrow();
        assertThat(question.getSectionId()).isEqualTo(222);
        assertThat(question.getDifficulty()).isEqualTo("상");
        assertThat(question.getAnswer()).isEqualTo(4);
        assertThat(question.getImageUrl()).isEqualTo(oldQuestionUrl);
        mvc.perform(get("/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.option1").value("수정1")).andExpect(jsonPath("$.option2").value("수정2"))
                .andExpect(jsonPath("$.option3").value("수정3")).andExpect(jsonPath("$.option4").value("수정4"))
                .andExpect(jsonPath("$.shortExplanation").value("수정 간단"))
                .andExpect(jsonPath("$.detailedExplanation.content").value("수정 상세"))
                .andExpect(jsonPath("$.detailedExplanation.imageUrl").value(oldExplanationUrl));
        mvc.perform(get("/api/v1/past-exam/{id}", links.findById(linkId).orElseThrow().getPastExamId())
                        .with(user("student").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].questionText").value("수정 지문"))
                .andExpect(jsonPath("$.questions[0].option4").value("수정4"))
                .andExpect(jsonPath("$.questions[0].answer").doesNotExist());
        commit();
        assertThat(path(oldQuestionUrl)).exists(); assertThat(path(oldExplanationUrl)).exists();
    }

    @Test
    void replacesBothImagesAndDeletesOldFilesOnlyAfterCommit() throws Exception {
        mvc.perform(request(questionId, body()).file(image("questionImage")).file(image("explanationImage"))
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        var question = questions.findById(questionId).orElseThrow();
        String newQuestionUrl = question.getImageUrl();
        String newExplanationUrl = explanations.findById(question.getDetailExplanationId()).orElseThrow().getImageUrl();
        assertThat(newQuestionUrl).isNotEqualTo(oldQuestionUrl);
        assertThat(newExplanationUrl).isNotEqualTo(oldExplanationUrl);
        assertThat(path(oldQuestionUrl)).exists(); assertThat(path(oldExplanationUrl)).exists();
        commit();
        assertThat(path(newQuestionUrl)).exists(); assertThat(path(newExplanationUrl)).exists();
        mvc.perform(get("/api/v1/file/{type}/{name}", "기출문제", path(newQuestionUrl).getFileName().toString()))
                .andExpect(status().isOk()).andExpect(content().bytes(image("questionImage").getBytes()));
        mvc.perform(get("/api/v1/file/{type}/{name}", "explanation", path(newExplanationUrl).getFileName().toString()))
                .andExpect(status().isOk()).andExpect(content().bytes(image("explanationImage").getBytes()));
        assertThat(path(oldQuestionUrl)).doesNotExist(); assertThat(path(oldExplanationUrl)).doesNotExist();
    }

    @Test
    void deletesImagesIndependentlyAndRemovesEmptyExplanation() throws Exception {
        var body = body(); body.put("deleteQuestionImage", true);
        mvc.perform(request(questionId, body).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(questions.findById(questionId).orElseThrow().getImageUrl()).isNull();
        assertThat(explanations.findById(explanationId).orElseThrow().getImageUrl()).isEqualTo(oldExplanationUrl);
        body.put("deleteExplanationImage", true); body.remove("detailedExplanation");
        mvc.perform(request(questionId, body).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(questions.findById(questionId).orElseThrow().getDetailExplanationId()).isNull();
        assertThat(path(oldQuestionUrl)).exists(); assertThat(path(oldExplanationUrl)).exists();
        commit();
        assertThat(path(oldQuestionUrl)).doesNotExist(); assertThat(path(oldExplanationUrl)).doesNotExist();
    }

    @Test
    void sharedExplanationUsesCopyAndKeepsSharedImage() throws Exception {
        var other = questions.save(new QuestionEntity(null, explanationId, 111, "모의고사", "다른 문제", oldQuestionUrl,
                "1", "2", "3", "4", 1, null, "중", null));
        var body = body(); body.put("deleteExplanationImage", true); body.put("deleteQuestionImage", true);
        mvc.perform(request(questionId, body).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(questions.findById(questionId).orElseThrow().getDetailExplanationId()).isNotEqualTo(explanationId);
        assertThat(questions.findById(other.getQuestionId()).orElseThrow().getDetailExplanationId()).isEqualTo(explanationId);
        assertThat(explanations.findById(explanationId).orElseThrow().getContent()).isEqualTo("기존 상세");
        commit();
        assertThat(path(oldExplanationUrl)).exists(); assertThat(path(oldQuestionUrl)).exists();
    }

    @Test
    void movesRoundAndOrderWithoutCreatingDuplicateLink() throws Exception {
        var body = body(); body.put("pastExamRound", 41); body.put("questionOrder", 7);
        mvc.perform(request(questionId, body).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(links.findAllByQuestionId(questionId)).hasSize(1);
        assertThat(links.findById(linkId).orElseThrow().getQuestionOrder()).isEqualTo(7);
        assertThat(exams.findById(links.findById(linkId).orElseThrow().getPastExamId()).orElseThrow().getPastExamRound()).isEqualTo(41);
    }

    @Test
    void synchronizesMockTagsIncludingClear() throws Exception {
        var mock = questions.save(new QuestionEntity(null, null, 111, "모의고사", "모의", null, "1", "2", "3", "4", 1, null, "중", null));
        tags.save(new MockPastTagEntity(mock.getQuestionId(), 35)); tags.save(new MockPastTagEntity(mock.getQuestionId(), 39));
        var body = body(); body.remove("pastExamRound"); body.remove("questionOrder"); body.put("tags", List.of(39, 40));
        mvc.perform(request(mock.getQuestionId(), body).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(tags.findPastExamRoundsByQuestionId(mock.getQuestionId())).containsExactly(39,40);
        body.put("tags", List.of());
        mvc.perform(request(mock.getQuestionId(), body).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(tags.findPastExamRoundsByQuestionId(mock.getQuestionId())).isEmpty();
    }

    @Test
    void rejectsDuplicateOrderAndLeavesOriginalData() throws Exception {
        var other = questions.save(new QuestionEntity(null, null, 111, "기출문제", "다른", null, "1", "2", "3", "4", 1, null, "중", null));
        links.save(new PastExamQuestionEntity(other.getQuestionId(), links.findById(linkId).orElseThrow().getPastExamId(), 2));
        var body = body(); body.put("questionOrder", 2);
        mvc.perform(request(questionId, body).with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        assertThat(questions.findById(questionId).orElseThrow().getQuestionText()).isEqualTo("기존 지문");
    }

    @Test
    void databaseFailureRollsBackAndRemovesNewFilesOnly() throws Exception {
        persistSeed();
        Set<Path> original;
        try (var files = Files.walk(imageRoot)) { original = new HashSet<>(files.filter(Files::isRegularFile).toList()); }
        jdbc.execute("ALTER TABLE questions ADD CONSTRAINT admin_update_test_check CHECK (question_text <> 'DB_FAIL')");
        try {
            var body = body(); body.put("questionText", "DB_FAIL"); body.put("pastExamRound", 89);
            mvc.perform(request(questionId, body).file(image("questionImage")).file(image("explanationImage"))
                            .with(user("admin").roles("ADMIN"))).andExpect(status().isInternalServerError());
            try (var files = Files.walk(imageRoot)) { assertThat(files.filter(Files::isRegularFile).toList()).containsExactlyInAnyOrderElementsOf(original); }
            assertThat(questions.findById(questionId).orElseThrow().getQuestionText()).isEqualTo("기존 지문");
            assertThat(explanations.findById(explanationId).orElseThrow().getContent()).isEqualTo("기존 상세");
            assertThat(exams.findByPastExamRound(89)).isEmpty();
            assertThat(path(oldQuestionUrl)).exists(); assertThat(path(oldExplanationUrl)).exists();
        } finally {
            jdbc.execute("ALTER TABLE questions DROP CONSTRAINT admin_update_test_check");
        }
    }

    @Test
    void secondUploadFailureRollsBackFirstUploadAndNewRound() throws Exception {
        persistSeed();
        Set<Path> original;
        try (var files = Files.walk(imageRoot)) { original = new HashSet<>(files.filter(Files::isRegularFile).toList()); }
        var body = body(); body.put("pastExamRound", 88);
        mvc.perform(request(questionId, body).file(image("questionImage"))
                        .file(new MockMultipartFile("explanationImage", "bad.png", "image/png", new byte[]{1,2,3}))
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        assertThat(exams.findByPastExamRound(88)).isEmpty();
        assertThat(questions.findById(questionId).orElseThrow().getImageUrl()).isEqualTo(oldQuestionUrl);
        try (var files = Files.walk(imageRoot)) { assertThat(files.filter(Files::isRegularFile).toList()).containsExactlyInAnyOrderElementsOf(original); }
    }

    @Test
    void rejectsBadFieldsMissingQuestionAndUnauthorizedRequests() throws Exception {
        var body = body(); body.put("answer", 5);
        mvc.perform(request(questionId, body).with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(request(Integer.MAX_VALUE, body()).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(request(questionId, body())).andExpect(status().isUnauthorized());
        mvc.perform(request(questionId, body()).with(user("student").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/questions/{id}", questionId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUploadWithDeletionFlag() throws Exception {
        var body = body(); body.put("deleteQuestionImage", true);
        mvc.perform(request(questionId, body).file(image("questionImage")).with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        assertThat(questions.findById(questionId).orElseThrow().getImageUrl()).isEqualTo(oldQuestionUrl);
    }

    @Test
    void corsAllowsPutAndStorageMappingRejectsForeignOrUnsafeUrls() throws Exception {
        mvc.perform(options("/api/admin/questions/{id}", questionId)
                        .header("Origin", "https://localhost:3000").header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")));
        assertThat(storage.storageKeyFromUrl("https://foreign.test/api/v1/file/explanation/a.png")).isEmpty();
        assertThat(storage.storageKeyFromUrl("/api/v1/file/explanation/%2e%2e%2fsecret.png")).isEmpty();
        assertThat(storage.storageKeyFromUrl("/api/v1/file/%EA%B8%B0%EC%B6%9C%EB%AC%B8%EC%A0%9C/a.png"))
                .contains("pastExamQuestion/a.png");
    }

    @Test
    void replacesOnlyExplanationImageThroughCommittedHttpTransaction() throws Exception {
        persistSeed();
        mvc.perform(request(questionId, body()).file(image("explanationImage"))
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        var question = questions.findById(questionId).orElseThrow();
        assertThat(question.getImageUrl()).isEqualTo(oldQuestionUrl);
        String newUrl = explanations.findById(question.getDetailExplanationId()).orElseThrow().getImageUrl();
        assertThat(path(oldQuestionUrl)).exists(); assertThat(path(oldExplanationUrl)).doesNotExist();
        assertThat(path(newUrl)).exists();
    }

    @Test
    void validatesRequiredFieldsDifficultySectionRoundOrderAndTags() throws Exception {
        List<Map<String,Object>> invalid = new ArrayList<>();
        for (String field : List.of("questionText", "option1", "option2", "option3", "option4", "answer", "difficulty", "sectionId", "pastExamRound", "questionOrder")) {
            var body = body(); body.remove(field); invalid.add(body);
        }
        for (String field : List.of("questionText", "option1", "option2", "option3", "option4", "difficulty")) {
            var body = body(); body.put(field, " "); invalid.add(body);
        }
        for (String field : List.of("answer", "pastExamRound", "questionOrder")) {
            var body = body(); body.put(field, 0); invalid.add(body);
        }
        var invalidSection = body(); invalidSection.put("sectionId", 999); invalid.add(invalidSection);
        var invalidTags = body(); invalidTags.put("tags", List.of(35,35)); invalid.add(invalidTags);
        var invalidRound = body(); invalidRound.put("tags", List.of(-1)); invalid.add(invalidRound);
        for (var body : invalid) {
            mvc.perform(request(questionId, body).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VF"));
        }
        assertThat(questions.findById(questionId).orElseThrow().getQuestionText()).isEqualTo("기존 지문");
    }
}
