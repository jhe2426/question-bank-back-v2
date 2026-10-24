# 관리자 문제 삭제 API

`DELETE /api/admin/questions/{questionId}` — 기존 JWT 인증, `ROLE_ADMIN` 필요.

```bash
curl -X DELETE 'http://localhost:4000/api/admin/questions/501' \
  -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
```

성공 응답은 HTTP 200입니다.

```json
{"code":"SU","message":"Success."}
```

존재하지 않는 문제는 HTTP 404입니다.

```json
{"code":"NQI","message":"Question ID does not exist."}
```

사용자 풀이, 오답, 응시 결과 또는 기타 DB 참조가 발견되면 HTTP 409입니다.

```json
{"code":"QIU","message":"Question is referenced by learning records or other data."}
```

## DB 참조와 보호 규칙

구현 전 로컬 MySQL `information_schema.COLUMNS`, `KEY_COLUMN_USAGE`, `REFERENTIAL_CONSTRAINTS`를 읽기 전용으로 조회했습니다. 스키마의 `question_id` 참조 컬럼과 `questions`를 참조하는 FK를 확인했습니다.

| 테이블 | 참조 | 실제 FK 삭제 규칙 | 처리 |
|---|---|---|---|
| `mock_past_tags` | `question_id` | NO ACTION | 안전한 삭제에서 해당 행 제거 |
| `past_exam_questions` | `question_id`, `past_exam_id` | NO ACTION | 해당 문제 연결만 제거, 회차 행 유지 |
| `user_solved_history` | `question_id` | NO ACTION | 행이 있으면 409 |
| `user_incorrect_questions` | `question_id` | NO ACTION | 행이 있으면 409 |
| `group_questions` | `question_id` | NO ACTION | 행이 있으면 409 |
| `questions` | `detail_explanation_id` | NO ACTION | 문제 삭제 뒤 공유 여부를 확인하고 고아 해설만 제거 |
| `group_questions` | `group_id` → `user_problem_groups` | NO ACTION | 그룹/채점 데이터는 삭제하지 않음 |

실제 스키마에서 문제 소프트 삭제 컬럼은 발견하지 못했습니다. 등록된 FK를 우회하거나 학습 이력을 연쇄 삭제하지 않습니다. 명시 확인한 학습 기록이 없어도 다른 FK가 삭제를 막으면 DB 트랜잭션 전체를 롤백하고 409를 반환합니다.

## 이미지와 트랜잭션

메타데이터 삭제와 문제 삭제를 트랜잭션으로 수행합니다. 이미지 파일은 DB 커밋 후에만 삭제를 시도합니다. 공통 이미지 정리 서비스가 문제·해설에 같은 URL 참조가 남아 있는지 확인합니다. 고아 상세 해설 행도 함께 정리합니다. 기출 회차 행은 보존합니다.

파일 삭제는 업로드 디렉터리 아래의 관리 이미지 키만 허용하며 `..` 경로와 외부를 가리키는 심볼릭 링크 디렉터리를 거부합니다. 이미지 정리는 커밋 콜백으로 수행되므로 파일 삭제 실패가 성공한 DB 삭제를 되돌리지 않습니다. 이 경우 오류 로그를 남기고 운영 환경에서 파일 정리가 재시도되어야 합니다.

## 테스트

```bash
./gradlew test --tests '*AdminQuestionDeleteIntegrationTest' \
  --tests '*AdminQuestionUpdateIntegrationTest' \
  --tests '*AdminQuestionQueryIntegrationTest' --tests '*AuthServiceImplementTest'
```

총 58개 테스트가 통과했습니다. 삭제 전용 H2 테스트에 실제와 동일한 NO ACTION FK를 두고 모의고사/기출 삭제, 태그·연결 제거, 회차 보존, 고아/공유 해설 및 이미지, 목록 제거, 404/409, 학습·그룹 FK 보호, 예외 롤백, 인증/권한과 경로 탈출 방지를 검증했습니다. 이미지 파일은 임시 디렉터리를 사용했습니다.
