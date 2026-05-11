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

## 현재 상태
- Spring Initializr로 프로젝트 골격 생성 완료
- 기본 의존성 및 빌드 설정(`build.gradle`) 적용 완료
- 이후 도메인 설계, API 구현, DB 스키마 설계 단계 진행 예정
