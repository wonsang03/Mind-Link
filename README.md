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
- `H2`: 로컬 개발용 인메모리 DB

## 로컬 실행 설정 (수정 사항)

`pom.xml`에만 의존성이 있고 `application.properties` 설정이 없어 실행이 실패하던 문제를 해결하기 위해 아래를 반영했습니다.

| 항목 | 내용 |
|------|------|
| **DB (JPA)** | H2 인메모리 DB URL·드라이버·계정 설정 추가 |
| **Thymeleaf** | `spring-boot-starter-thymeleaf` 의존성 추가 |
| **Spring Security** | 로컬 개발용 전체 경로 허용 (`SecurityConfig`) |
| **Spring AI / Oracle Vector** | API 키·DB 미설정 시 자동 설정 비활성화 (운영 연동 시 별도 설정 필요) |
| **프론트** | `templates/`, `static/` 화면 파일 통합 |
| **백엔드** | 페이지·커뮤니티 등 MVC 컨트롤러·서비스 (`com.mindlink`) |

로컬 실행: `./mvnw spring-boot:run` → http://localhost:8080

## 문서 (팀원 작성용)

각 영역별 상세 내용은 아래 파일에 정리합니다.

| 문서 | 설명 |
|------|------|
| [docs/frontend.md](docs/frontend.md) | 화면(UI) 템플릿·CSS |
| [docs/backend.md](docs/backend.md) | 서버 로직·설정 |
| [docs/db.md](docs/db.md) | DB 스키마·연동 |
| [docs/api.md](docs/api.md) | REST API 명세 |

## 현재 상태
- Spring Initializr 프로젝트 골격 및 기본 의존성 적용 완료
- 프론트엔드(Thymeleaf 템플릿·CSS) `main` 브랜치 통합 완료
- 로컬 실행을 위한 H2·Security·Thymeleaf 등 최소 설정 반영 완료
- Oracle·OpenAI 등 운영 연동 설정은 추후 `application.properties`에 추가 예정
