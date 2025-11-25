# ABAC 시나리오

## 개요

정책 엔진에 구현된 12가지 핵심 ABAC 시나리오와 각 시나리오에 대한 포괄적인 단위 테스트 설명

## 테스트 커버리지 요약

- **총 시나리오**: 12개
- **총 테스트 케이스**: 71개 이상
- **테스트 파일**: `CoreAbacScenariosTest.kt`
- **시나리오 파일**: `CoreAbacScenarios.kt`

시나리오는 다음을 포함.
- Kotlin DSL을 사용한 정책 정의
- 긍정 및 부정 시나리오를 다루는 3-4개의 테스트 케이스
- 엣지 케이스 처리

---

## 시나리오 상세

### 1. 직무 분리 기반 (Separation of Duties)

**목적**: 내부 통제를 유지하기 위해 자기 승인 방지

**정책**:
```kotlin
(action eq "Approve") implies {
    (user.role eq "Manager") and (resource.ownerId neq user.id)
}
```

**테스트 케이스**:
- ✅ `1-1` 관리자의 자기 승인 거부
- ✅ `1-2` 다른 관리자의 승인 허용
- ✅ `1-3` 비관리자의 승인 시도 거부

**검증**: 사용자 ID와 리소스 소유자 ID 비교

---

### 2. 할당 프로젝트 단위 기반

**목적**: 사용자는 할당된 프로젝트에만 접근 가능

**정책**:
```kotlin
(action in ["Read", "Edit"]) implies {
    resource.projectId inList user.activeProjects
}
```

**테스트 케이스**:
- ✅ `2-1` 활성 프로젝트의 사용자 허용
- ✅ `2-2` 프로젝트에 없는 사용자 거부
- ✅ `2-3` 범위 밖의 삭제 작업 허용

**검증**: 컬렉션 멤버십 검사 (IN 연산)

---

### 3. 환경 및 위치 기반

**목적**: 극비 리소스는 회사 네트워크와 기기 필요

**정책**:
```kotlin
(resource.classification eq "Top Secret") implies {
    (user.location eq "Office_Network") and (env.deviceType eq "Corporate_Laptop")
}
```

**테스트 케이스**:
- ✅ `3-1` 안전한 환경에서 극비 접근 허용
- ✅ `3-2` 홈 네트워크에서 극비 접근 거부
- ✅ `3-3` 개인 기기에서 극비 접근 거부
- ✅ `3-4` 모든 위치에서 비기밀 접근 허용

**검증**: 환경 객체 참조 및 다중 조건

---

### 4. 리소스 생명주기 기반

**목적**: 문서 상태에 따른 다른 권한

**정책**:
```kotlin
whenCase(resource.status) {
    "Draft" then (user.id eq resource.creatorId)
    "Under_Review" then (user.role eq "Editor")
    "Published" then (user.role eq "Public")
    elseCase false
}
```

**테스트 케이스**:
- ✅ `4-1` 작성자의 초안 접근 허용
- ✅ `4-2` 비작성자의 초안 접근 거부
- ✅ `4-3` 편집자의 검토 중 접근 허용
- ✅ `4-4` 공개된 문서에 대한 공개 접근 허용
- ✅ `4-5` 알 수 없는 상태 접근 거부

**검증**: whenCase를 사용한 패턴 매칭

---

### 5. 직책 / 계층 기반

**목적**: 관리자는 부하 직원의 성과 평가 조회 가능

**정책**:
```kotlin
(action eq "View_Performance_Review") implies {
    user.id eq resource.owner.managerId
}
```

**테스트 케이스**:
- ✅ `5-1` 관리자의 부하 직원 평가 조회 허용
- ✅ `5-2` 비관리자의 평가 조회 거부
- ✅ `5-3` 평가 외 작업 허용

**검증**: 중첩 객체 탐색 (resource.owner.managerId)

---

### 6. 목적 기반 PII (GDPR)

**목적**: PII 데이터는 특정 목적 정당성 필요

**정책**:
```kotlin
(resource.type eq "PII_Data") implies {
    (user.role eq "HR") and (action.purpose eq "Salary_Processing")
}
```

**테스트 케이스**:
- ✅ `6-1` 급여 처리를 위한 HR 접근 허용
- ✅ `6-2` 마케팅 목적의 HR 접근 거부
- ✅ `6-3` 목적과 관계없이 비HR 접근 거부
- ✅ `6-4` 비PII 데이터 접근 허용

**검증**: 액션 객체 속성 및 목적 검증

---

### 7. 시간 제한 위임

**목적**: 만료일이 있는 임시 승인 권한

**정책**:
```kotlin
(action eq "Approve") implies {
    (user.id eq resource.delegatedTo) and
    (currentTime between (delegation.startTime to delegation.endTime))
}
```

**테스트 케이스**:
- ✅ `7-1` 위임 기간 내 승인 허용
- ✅ `7-2` 위임 만료 후 승인 거부
- ✅ `7-3` 잘못된 위임자의 승인 거부

**검증**: between 함수를 사용한 시간 범위 비교

---

### 8. 위험 점수 기반

**목적**: 고위험 작업은 낮은 위험 점수 필요

**정책**:
```kotlin
(action eq "Delete_Table") implies {
    (user.role eq "Admin") and (user.riskScore lt 30)
}
```

**테스트 케이스**:
- ✅ `8-1` 낮은 위험 관리자의 삭제 허용
- ✅ `8-2` 높은 위험 관리자의 삭제 거부
- ✅ `8-3` 위험과 관계없이 비관리자의 삭제 거부
- ✅ `8-4` 비삭제 작업에 대한 고위험 관리자 허용

**검증**: 숫자 비교 (미만)

---

### 9. 리소스 할당량 및 속도 제한

**목적**: 구독 레벨에 따른 일일 사용 제한

**정책**:
```kotlin
(action in ["API_Call", "Download"]) implies {
    (user.subLevel eq "Basic") and (user.dailyUsage lt 100)
}
```

**테스트 케이스**:
- ✅ `9-1` 일일 한도 내 기본 사용자 허용
- ✅ `9-2` 일일 한도 초과 기본 사용자 거부
- ✅ `9-3` 높은 사용량의 프리미엄 사용자 허용
- ✅ `9-4` 추적되지 않는 작업에 대한 기본 사용자 허용

**검증**: 카운터 값 비교

---

### 10. 윤리적 장벽 (이해 충돌)

**목적**: 사용자가 충돌하는 클라이언트 데이터에 접근하는 것을 방지

**정책**:
```kotlin
(resource.client eq "Client_A") implies {
    not(user.assignedClients contains "Client_B")
}
```

**테스트 케이스**:
- ✅ `10-1` 충돌 없는 접근 허용
- ✅ `10-2` 이해 충돌 접근 거부
- ✅ `10-3` Client_A가 아닌 리소스 접근 허용
- ✅ `10-4` 빈 클라이언트 목록을 가진 사용자 허용

**검증**: NOT 논리와 함께 CONTAINS 연산

---

### 11. 세금계산서 승인 (직책 기반)

**목적**: 사용자 직책에 기반한 승인 권한 부여

**정책**:
```kotlin
(action eq "Approve_Tax_Invoice") implies
whenCase(resource.amount) {
    // 1백만원 미만 - 팀장
    (resource.amount lt 1000000) then (
        subject.position isIn ["TeamLeader", "DepartmentHead", "DivisionHead", "ExecutiveDirector"]
    )
    // 1백만원 ~ 1천만원 - 실장 이상
    (resource.amount lt 10000000) then (
        subject.position isIn ["DepartmentHead", "DivisionHead", "ExecutiveDirector"]
    )
    // 1천만원 ~ 1억원 - 본부장 이상
    (resource.amount lt 100000000) then (
        subject.position isIn ["DivisionHead", "ExecutiveDirector"]
    )
    // 1억원 이상 - 상무만
    (resource.amount gte 100000000) then (
        subject.position eq "ExecutiveDirector"
    )
    elseCase false
}
```

**테스트 케이스**:
- ✅ `11-1` 소액 금액에 대한 팀장 허용 (100만원 미만)
- ✅ `11-2` 중간 금액에 대한 팀장 거부 (500만원)
- ✅ `11-3` 중간 금액에 대한 실장 허용 (500만원)
- ✅ `11-4` 고액 금액에 대한 실장 거부 (5천만원)
- ✅ `11-5` 고액 금액에 대한 본부장 허용 (5천만원)
- ✅ `11-6` 초고액 금액에 대한 본부장 거부 (2억원)
- ✅ `11-7` 모든 금액에 대한 상무 허용
- ✅ `11-8` 100만원 경계 테스트
- ✅ `11-9` 상위 직책은 하위 금액 승인 가능
- ✅ `11-10` 올바른 직책이더라도 자기 승인 거부 (SoD 포함)
- ✅ `11-11` 올바른 직책을 가진 다른 사람의 승인 허용 (SoD 포함)
- ✅ `11-12` 자기 승인이 아니더라도 직책 부족 시 거부 (SoD 포함)
- ✅ `11-13` 포괄적 금액 구간 테스트 (11개 하위 테스트)

**검증**: 패턴 매칭(whenCase)을 사용한 금액 기반 직책 매칭

**직책 계층**:
```
팀장 (Team Leader)        → 100만원 미만
실장 (Department Head)    → 100만원 ~ 1천만원
본부장 (Division Head)    → 1천만원 ~ 1억원
상무 (Executive Director) → 1억원 이상
```

**목적**:
- 금액 구간 경계 테스트
- 직책 계층 검증
- 직무 분리(SoD) 원칙을 적용한 변형

---

### 12. 세금계산서 승인 (기능 단위 역할 부여 & 책임 소재에 따른 허가)

**목적**: 기능 단위 역할에 기반한 승인 권한 부여 시, 책임 소재에 따른 정책을 반영하는 시나리오

**시나리오 11과의 차이점**:
- **시나리오 11**: 금액 구간을 가진 계층적 직책 기반 접근 (팀장, 실장, 본부장, 상무)
- **시나리오 12**: 직책과 관계없이 사용자가 동등한 승인 권한을 가진 책임 소재 기반 접근

**사용 사례**:
- 조직 내 직책과 관계없이 승인 역량 보유
- 계층적 제어 vs 기능 기반 접근 제어

**정책 변형**:

#### 12-A: 역량 기반 (기본)
```kotlin
(action eq "Approve_Tax_Invoice") implies {
    subject.capabilities contains "Approve_Tax_Invoice"
}
```

#### 12-B: 명시적 사용자 목록
```kotlin
(action eq "Approve_Tax_Invoice") implies {
    subject.id in ["certified_accountant_001", "certified_accountant_002", "certified_accountant_003"]
}
```

#### 12-C: 권한 그룹
```kotlin
(action eq "Approve_Tax_Invoice") implies {
    subject.permissionGroups contains "TaxInvoiceApprovers"
}
```

#### 12-D: 하이브리드 (직책 OR 역량)
```kotlin
(action eq "Approve_Tax_Invoice") implies {
    (subject.position eq "ExecutiveDirector") or
    (subject.capabilities contains "Approve_Tax_Invoice")
}
```

#### 12-E: SoD를 포함한 역량
```kotlin
(action eq "Approve_Tax_Invoice") implies {
    (subject.capabilities contains "Approve_Tax_Invoice") and
    (resource.ownerId neq subject.id)
}
```

**테스트 케이스**:
- ✅ `12-1` 승인 역량을 가진 사용자 허용 (역량을 가진 스태프)
- ✅ `12-2` 승인 역량이 없는 사용자 거부
- ✅ `12-3` ID로 승인된 사용자 허용 (certified_accountant_002)
- ✅ `12-4` ID로 승인되지 않은 사용자 거부
- ✅ `12-5` 권한 그룹에 속한 사용자 허용
- ✅ `12-6` 권한 그룹에 속하지 않은 사용자 거부
- ✅ `12-7` 하이브리드 - 역량 없는 임원 허용
- ✅ `12-8` 하이브리드 - 역량 있는 스태프 허용
- ✅ `12-9` 하이브리드 - 역량이나 직책이 없는 스태프 거부
- ✅ `12-10` SoD를 포함한 역량 - 역량을 가진 비작성자 허용
- ✅ `12-11` SoD를 포함한 역량 - 역량이 있어도 작성자 거부
- ✅ `12-12` 사용자 목록 - 세 명의 승인된 회계사 모두 테스트
- ✅ `12-13` 역량 vs 직책 비교

**검증**: 사용자 역량 확인, 주체 식별자 검증, 권한 그룹 멤버십

---

## 테스트 케이스 설계 기준

### 1. 긍정 및 부정 테스트

각 시나리오는 다음을 모두 포함
- **긍정 테스트**: 유효한 접근이 허용되는지 검증
- **부정 테스트**: 무효한 접근이 거부되는지 검증

### 2. 엣지 케이스 커버리지

엣지 케이스 포함
- 빈 컬렉션
- Null 값 (isNull/isNotNull 연산자로 처리)
- 경계 조건 (예: 할당량 제한)
- 시간 기반 만료

### 3. 통합 테스팅

통합 테스트는 다음을 검증함
- 모든 정책이 저장될 수 있음
- 캐시 기능이 올바르게 작동함
- 여러 정책이 공존할 수 있음
