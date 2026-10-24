# 관리자 회원관리 API

두 API 모두 기존 JWT 인증과 `/api/admin/**`의 `ROLE_ADMIN` 권한 검사를 사용합니다.
회원 목록과 학습 현황은 `Role.USER` 계정만 대상으로 하며 관리자 계정은 포함하지 않습니다.

## 회원 목록 조회

`GET /api/admin/users`

| 쿼리 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| `keyword` | 없음 | 이름 또는 전화번호 부분 검색, 앞뒤 공백 제거, 최대 50자. 전화번호의 하이픈은 무시 |
| `page` | `0` | 0 이상의 페이지 번호 |
| `size` | `20` | 1~100 |

```bash
curl --get 'http://localhost:4000/api/admin/users' \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  --data-urlencode 'keyword=김하은' \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=20'
```

```json
{
  "code": "SU",
  "message": "Success.",
  "users": [
    {
      "userId": "haeun01",
      "userName": "김하은",
      "phoneNumber": "01012345678"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20,
  "last": true
}
```

검색 결과가 없으면 HTTP 200과 빈 `users` 배열을 반환합니다.

## 회원별 학습 현황 조회

`GET /api/admin/users/{userId}/learning-summary`

```bash
curl 'http://localhost:4000/api/admin/users/haeun01/learning-summary' \
  --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
```

```json
{
  "code": "SU",
  "message": "Success.",
  "userId": "haeun01",
  "userName": "김하은",
  "phoneNumber": "01012345678",
  "totalSolvedCount": 120,
  "totalSolveSeconds": 12300,
  "sectionSummaries": [
    {
      "sectionId": 111,
      "sectionName": "데이터의 이해",
      "solvedCount": 35,
      "totalSolveSeconds": null
    }
  ]
}
```

존재하지 않는 회원 또는 관리자 계정을 조회하면 HTTP 404를 반환합니다.

```json
{"code":"NUI","message":"User ID does not exist."}
```

학습 기록이 없는 회원은 정상 응답을 반환합니다.

```json
{
  "code": "SU",
  "message": "Success.",
  "userId": "new-user",
  "userName": "신규회원",
  "phoneNumber": "01000000000",
  "totalSolvedCount": 0,
  "totalSolveSeconds": null,
  "sectionSummaries": []
}
```

## 집계 기준과 시간 데이터

- `totalSolvedCount`와 섹션의 `solvedCount`는 `user_solved_history`의 `COUNT(DISTINCT question_id)`입니다. 같은 문제를 여러 번 푼 기록은 한 문항으로 셉니다.
- 섹션은 문제의 현재 `questions.section_id`와 `sections.section_name`을 기준으로 DB에서 `GROUP BY`합니다.
- 전체 시간은 실제로 저장된 `user_problem_groups.time_spent_seconds`의 합계입니다. 학습 그룹 기록이 없으면 `null`입니다.
- 현재 DB의 `time_spent_seconds`는 `NOT NULL`이며, 확인 시점의 5개 그룹 기록에 모두 초 값이 저장되어 있었습니다.
- 문제별 풀이 기록인 `user_solved_history`에는 풀이 시간과 `group_id`가 없습니다. 전체 시험 시간을 섹션에 배분할 근거가 없으므로 모든 섹션의 `totalSolveSeconds`는 `null`입니다.

섹션별 시간을 제공하려면 최소한 문제 풀이 단위에 `solve_seconds`와 해당 응시 그룹을 식별할 `group_id`를 저장해야 합니다. 채점 요청도 문제별 시간 값을 받아 검증하고 `user_solved_history`에 함께 기록해야 합니다. 이후 `question_id`로 섹션을 JOIN하여 `SUM(solve_seconds)`를 계산할 수 있습니다. 이 변경에는 DB 마이그레이션과 기존 기록의 `null` 유지 정책이 필요하며 이번 조회 API에는 포함하지 않았습니다.

## 구현 파일

- `controller/admin/AdminUserController.java`
- `service/admin/AdminUserQueryService.java`
- `common/dto/request/admin/GetAdminUsersRequestDto.java`
- `common/dto/response/admin/AdminUserListItemDto.java`
- `common/dto/response/admin/GetAdminUserListResponseDto.java`
- `common/dto/response/admin/GetAdminUserLearningSummaryResponseDto.java`
- `repository/UserRepository.java`
- `repository/UserSolvedHistoryRepository.java`
- `repository/UserProblemGroupRepository.java`
- `repository/resultSet/AdminSectionLearningSummaryResultSet.java`
- `common/exception/ErrorCode.java`
