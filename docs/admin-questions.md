# 관리자 문제 목록 조회

`GET /api/admin/questions` — 기존 JWT 인증 및 `ROLE_ADMIN` 권한 검증 적용.
API 계약: [OpenAPI](admin-questions.openapi.json).

| 파라미터 | 규칙 |
| --- | --- |
| questionType | 필수: `기출문제` 또는 `모의고사` |
| pastExamRound | 기출문제에서 필수, 실제 회차인 1 이상의 정수 |
| difficulty | 모의고사에서 필수: `상`, `중`, `하` |
| page | 기본 0, 0 이상의 정수 |
| size | 기본 20, 1~100의 정수. 초과 시 400 |

기출문제는 문제 순서 오름차순(동률은 연결 ID 오름차순), 모의고사는 문제 ID 내림차순입니다.
`sort`로 순서를 변경할 수 없습니다. 선택한 유형에 해당하지 않는 조건은 필터로 사용하지 않습니다.

```bash
curl --get 'http://localhost:8080/api/admin/questions' \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  --data-urlencode 'questionType=기출문제' \
  --data-urlencode 'pastExamRound=39' \
  --data-urlencode 'page=0' --data-urlencode 'size=20'

curl --get 'http://localhost:8080/api/admin/questions' \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  --data-urlencode 'questionType=모의고사' \
  --data-urlencode 'difficulty=중' \
  --data-urlencode 'page=1' --data-urlencode 'size=20'
```

아래는 해당 회차에 한 문제가 등록되어 있을 때의 예시입니다. 운영 DB에서 추출한 데이터가 아닙니다.

```json
{
  "code": "SU",
  "message": "Success.",
  "questions": [
    {
      "questionId": 501,
      "questionType": "기출문제",
      "questionText": "문제 지문",
      "difficulty": "중",
      "imageUrl": "/api/v1/file/기출문제/example.png",
      "sectionId": 111,
      "pastExamRound": 39,
      "questionOrder": 5
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20,
  "last": true
}
```

모의고사는 `pastExamRound`, `questionOrder`가 모두 `null`입니다.
조회 결과가 없으면 200과 다음 응답을 반환합니다.

```json
{"code":"SU","message":"Success.","questions":[],"totalElements":0,"totalPages":0,"page":0,"size":20,"last":true}
```

유형/필수 조건/숫자 형식/페이징 범위가 잘못되면 400입니다.

```json
{"code":"VF","message":"Validation failed."}
```

## 검증

```bash
./gradlew test --tests '*AdminQuestionQueryIntegrationTest' --tests '*AuthServiceImplementTest'
```

통합 테스트는 별도 H2 메모리 DB와 실제 MVC·서비스·JPA 쿼리·SecurityFilterChain을 사용합니다.
39회/40회 분리, 순서, 중 난이도 필터, 25개 데이터의 두 페이지, 빈 회차, 크기 제한,
잘못된 조건, 인증/권한, 기존 일반 사용자 기출/모의고사 조회, JSON 등록 후 조회를 검증합니다.
기출 첫 페이지의 SQL 실행 횟수는 목록과 count를 합쳐 2회로 검증합니다.
JWT 토큰 파싱과 이미지 저장은 테스트 대역을 사용합니다. 기존 JWT 필터의 역할 판정은 실제 코드로 검증합니다.
실제 MySQL 및 실제 서명 JWT를 사용하는 배포 환경 호출은 이 테스트의 범위에 포함하지 않습니다.
기존 `IncorrectQuestionTransactionTest`는 외부 MySQL과 사전 데이터가 필요하므로 위 명령에 포함하지 않습니다.
