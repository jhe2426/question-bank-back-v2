# 관리자 문제 수정 API

`PUT /api/admin/questions/{questionId}`

ADMIN JWT 인증이 필요합니다. 문제 유형, 문제 ID, 생성 시각은 수정하지 않습니다.
성공 시 `200 {"code":"SU","message":"Success."}`를 반환하며, 기존 상세 조회 API로 수정된 데이터를 다시 조회할 수 있습니다.

## 요청

`multipart/form-data` 파트:

| 파트 | 형식 | 필수 | 설명 |
|---|---|---|---|
| question | application/json | 예 | 아래 수정 데이터 |
| questionImage | 이미지 파일 | 아니오 | 새 문제 이미지 |
| explanationImage | 이미지 파일 | 아니오 | 새 상세 해설 이미지 |

`question.json` 예시 — 기출문제:

```json
{
  "questionText": "수정한 문제 지문",
  "option1": "선택지 1",
  "option2": "선택지 2",
  "option3": "선택지 3",
  "option4": "선택지 4",
  "answer": 3,
  "difficulty": "중",
  "sectionId": 111,
  "shortExplanation": "수정한 간단 해설",
  "detailedExplanation": {
    "content": "수정한 상세 해설"
  },
  "deleteQuestionImage": false,
  "deleteExplanationImage": false,
  "pastExamRound": 39,
  "questionOrder": 5,
  "tags": []
}
```

```bash
curl --request PUT 'http://localhost:4000/api/admin/questions/501' \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  --form 'question=@question.json;type=application/json' \
  --form 'questionImage=@question.png;type=image/png' \
  --form 'explanationImage=@explanation.png;type=image/png'
```

이미지를 유지하거나 삭제할 때는 해당 파일 파트를 생략합니다.
모의고사는 `pastExamRound`, `questionOrder`를 생략하거나 null로 보내고 `tags: [35, 39]`처럼 회차 배열을 보냅니다.
기존 상세 응답의 `tags` 배열을 그대로 사용할 수 있습니다. 기존 JSON 일괄 등록 API의 태그 형식은 변경되지 않습니다.

## 값과 이미지 처리 규칙

- 지문·선택지 1~4·정답·난이도·분류는 매번 보내야 합니다.
- 정답: 1~4, 난이도: 상/중/하, 분류: 실제 존재하는 sectionId.
- 지문·선택지·간단 해설·상세 해설 내용은 현재 엔티티 문자열 컬럼에 맞춰 최대 255자입니다.
- 간단 해설을 생략하거나 null로 보내면 지워집니다.
- `detailedExplanation`을 생략/null로 보내거나 `content`를 null/빈 문자열로 보내면 상세 해설 텍스트가 지워집니다. 이미지 유지 여부는 별도 플래그로 결정합니다.
- 상세 해설의 텍스트와 이미지가 모두 없으면 문제에서 해설 연결을 해제합니다.
- 태그 생략 또는 `[]`는 전체 태그 제거입니다. null, 중복, 1 미만의 회차는 허용하지 않습니다.
- 기출문제에는 1 이상의 `pastExamRound`, `questionOrder`가 필수이며, 비어 있지 않은 태그는 허용하지 않습니다.
- 모의고사에 기출 회차/문제 순서를 지정하면 검증 오류입니다.
- 기출 회차 안에서 다른 문제가 이미 사용 중인 문제 순서는 거절합니다.

두 이미지는 다음 규칙을 각각 적용합니다.

| 새 파일 | 삭제 플래그 | 결과 |
|---|---|---|
| 없음 | false 또는 생략 | 기존 이미지 유지 |
| 있음 | false 또는 생략 | 새 이미지로 교체 |
| 없음 | true | 이미지 연결 해제 |
| 있음 | true | 400 검증 오류 |

빈 파일 파트는 오류입니다. PNG/JPG/JPEG/WebP, 파일당 최대 10MB와 기존 파일 시그니처 검증을 적용합니다.
문제 URL과 해설 URL은 기존 `AdminImageStorageService`의 공개 URL 설정을 사용합니다.
해설의 `/api/v1/file/explanation/{fileName}` 경로를 기존 파일 조회 서비스에 연결했습니다.

## 저장과 파일 정리

- 수정 전체를 트랜잭션으로 처리합니다. 해당 문제와 대상 회차에 쓰기 잠금을 사용하고 수정 트랜잭션은 SERIALIZABLE 격리를 사용합니다.
- 회차 조회/생성 코드는 `AdminPastExamService.getOrCreatePastExamId()`로 추출하여 기존 등록과 수정에서 재사용합니다.
- 기출 연결 행이 있으면 ID를 유지해 갱신합니다. 연결이 없는 데이터는 한 행을 만들고, 연결이 여러 개인 비정상 데이터는 수정 요청을 거절합니다.
- 모의고사 태그는 기존 값과 비교해 추가/제거된 태그만 반영합니다.
- 다른 문제에서 공유하는 상세 해설은 복사하여 수정합니다. 더 이상 참조되지 않는 이전 해설 행만 정리합니다.
- 롤백 시 새로 저장한 파일을 삭제합니다. 트랜잭션 결과가 불명확하면 파일을 보존하고 로그를 남깁니다.
- 기존 파일은 커밋 후 별도 트랜잭션에서 문제·해설의 참조 여부를 확인하고 삭제합니다. 다른 참조가 있으면 보존합니다.
- URL을 로컬 저장 키로 변환할 수 있는 관리 대상 파일만 삭제합니다. 외부 URL이나 해석할 수 없는 기존 URL의 파일은 보존합니다.
- 커밋 후 파일 삭제 실패는 로그를 남기며 이미 성공한 DB 수정을 실패 응답으로 바꾸지 않습니다. 프로세스 중단/파일시스템 장애에 대한 영속적인 재시도 큐는 추가하지 않았습니다.

## 오류

| 상태 | code | 조건 |
|---|---|---|
| 400 | VF | 필수값/정답/난이도/회차/순서/태그 오류, 중복 순서, 잘못된 이미지, 업로드·삭제 충돌 |
| 401 | AF | 인증 없음 |
| 403 | 기존 Security 응답 | ADMIN 권한 없음 |
| 404 | NQI | 문제 없음 |
| 500 | DBE 또는 ISE | DB 또는 저장소 오류. 트랜잭션 롤백 및 새 파일 정리 |

## 검증

```bash
./gradlew test --tests '*AdminQuestionUpdateIntegrationTest' \
  --tests '*AdminQuestionQueryIntegrationTest' --tests '*AuthServiceImplementTest'
```

H2 메모리 DB, 실제 MVC/서비스/JPA, 임시 디렉터리의 실제 이미지 저장소를 사용합니다.
인증 토큰 파싱 및 분류 존재 확인은 테스트 대역으로 분리합니다.

검증 항목: 지문·선택지·정답·난이도·분류·해설 변경, 이미지 유지/교체/삭제,
커밋 전 기존 파일 보존, 공유 해설/이미지 보존, 회차·순서 변경과 중복 방지,
태그 변경/제거, 실제 DB CHECK 제약 위반에 의한 롤백과 새 파일 삭제,
두 번째 이미지 검증 실패 시 첫 파일 정리와 새 회차 롤백,
일반 사용자 조회의 수정된 지문/선택지 확인과 정답 비공개 유지,
파일 API에서 새 문제·해설 이미지 조회, ADMIN 제한, CORS PUT, 기존 목록·상세·등록 회귀.

실제 MySQL에서의 동시 수정 경쟁 및 실제 서명 JWT 호출은 이 테스트로 검증하지 않습니다.
기존 `IncorrectQuestionTransactionTest`는 별도의 MySQL 및 사전 데이터가 필요하여 실행 대상에서 제외했습니다.

## 변경 파일

- `common/dto/request/admin/PutAdminQuestionRequestDto.java`: 수정 요청 DTO.
- `controller/admin/AdminQuestionController.java`: PUT multipart 엔드포인트.
- `service/admin/AdminQuestionUpdateService.java`: 검증, 갱신, 트랜잭션 및 파일 수명 관리.
- `service/admin/AdminPastExamService.java`: 공통 회차 조회/생성.
- `service/admin/AdminQuestionService.java`: 기존 회차 메서드를 공통 서비스에 위임.
- `service/admin/AdminImageCleanupService.java`: 커밋 후 파일 참조 확인/삭제.
- `service/admin/AdminImageStorageService.java`: 저장 URL을 안전한 저장 키로 변환.
- `service/implement/FileServiceImplement.java`: 해설 이미지 경로 매핑 및 파일 경로 결합.
- `common/entity/QuestionEntity.java`, `DetailedExplanationEntity.java`, `PastExamQuestionEntity.java`: 갱신 메서드.
- `repository/QuestionRepository.java`, `DetailedExplanationRepository.java`, `PastExamRepository.java`, `PastExamQuestionRepository.java`, `MockPastTagRepository.java`: 잠금, 참조 확인, 연결/태그 조회.
- `config/WebSecurityConfig.java`: 기존 허용 출처에 PUT CORS 허용.
- `AdminQuestionUpdateIntegrationTest.java`: 수정 통합 테스트 추가.
- `AdminQuestionQueryIntegrationTest.java`: 새 의존성의 테스트 컨텍스트 등록.
- `docs/admin-question-update.md`, `docs/admin-questions.openapi.json`: 사용법과 계약.
