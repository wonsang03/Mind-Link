# Mind-Link
목포대 웹프2 5조 팀플

## 초기 생성 설정(Spring Initializr)
- Project: `Maven`
- Language: `Java`
- Spring Boot: `4.0.6`
- Group: `com.example`
- Artifact: `demo`
- Package Name: `com.example.demo`
- Packaging: `jar`
- Java: `17`
- Configuration: `Properties`

## 의존성 구성과 역할
- `Spring Web`: REST API 및 웹 요청/응답 처리(MVC 기반)
- `Lombok`: 반복적인 getter/setter/constructor 코드 축약
- `Spring Boot DevTools`: 개발 중 자동 재시작 및 편의 기능 지원
- `Spring Data JPA`: 데이터베이스 접근 계층(JPA/Hibernate) 구성
- `Oracle Vector Database`: Oracle 벡터 저장소 연동(임베딩 저장/검색)
- `Spring Security`: 인증/인가 및 보안 필터 체인 구성
- `OpenAI`: OpenAI 모델 연동(LLM/임베딩 호출)
- `Validation`: 요청 DTO/엔티티 입력값 검증
- `Thymeleaf`: 서버 사이드 HTML 템플릿 렌더링

> **DB는 Oracle을 사용합니다.** (팀 개발·운영 기준)

## 오류났던부분

처음에는 `pom.xml`에 **라이브러리(의존성)만 받아 둔 상태**였고,  
`application.properties` **설정 파일은 거의 비어 있었습니다.**

그래서 프로그램을 켤 때 Spring Boot가 이렇게 반응했습니다.

- JPA를 쓰겠다고 했는데 → **DB가 어디 있는지** 모름 → 오류
- Security를 넣었는데 → **누구에게 열어 줄지** 모름 → 막히거나 오류
- OpenAI를 넣었는데 → **API 키가 없음** → 오류

그래서 **일단 내 PC에서만** 돌아가게 아래를 임시로 넣었습니다.

| 임시로 넣은 것 | 왜 넣었는지 |
|----------------|-------------|
| H2 + DB 설정 | Oracle 연결 전, 로컬에서만 실행 테스트 |
| Thymeleaf | 통합해 둔 HTML 화면 보이게 |
| Security 전체 허용 | 로그인 설정 전에 화면부터 확인 |
| Spring AI 자동 설정 끄기 | API 키 없이도 서버가 켜지게 |

Oracle·OpenAI는 나중에 `application.properties`에 **팀에서 쓸 값**을 넣을 예정

## 로컬에서 실행

```bash
./mvnw spring-boot:run
```

브라우저: **http://localhost:8080**

## 문서 (팀원 작성용)

| 문서 | 설명 |
|------|------|
| [docs/frontend.md](docs/frontend.md) | 화면(UI) 템플릿·CSS |
| [docs/backend.md](docs/backend.md) | 서버 로직·설정 |
| [docs/db.md](docs/db.md) | Oracle DB 스키마·연동 |
| [docs/api.md](docs/api.md) | REST API 명세 |

## 현재 상태
- Spring Initializr 프로젝트 골격 및 기본 의존성 적용 완료
- 프론트엔드(Thymeleaf 템플릿·CSS) `main` 브랜치 통합 완료
- 로컬 실행용 임시 설정(H2, Security, Thymeleaf 등) 반영 완료
- Oracle DB·OpenAI 연동 설정은 추후 `application.properties`에 추가 예정
