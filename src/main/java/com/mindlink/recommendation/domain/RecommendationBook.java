package com.mindlink.recommendation.domain;

import com.mindlink.recommendation.EmotionCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 추천 도서 엔티티 (recommendation_books 테이블).
 * 스키마·시드는 sql/ORACLE_SETUP.sql 에서 수동 적재 (ddl-auto=none).
 */
@Entity
@Table(name = "recommendation_books")
@Getter @Setter @NoArgsConstructor
public class RecommendationBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmotionCategory emotion;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(length = 500)
    private String author;
    @Column(length = 500)
    private String publisher;
    @Column(length = 2000)
    private String link;

    @Column(length = 2000)
    private String image;

    @Lob
    @Column(nullable = true)
    private String description;

    // 네이버 API 자동 캐싱 시 중복 방지 기준 (수동 등록 도서는 NULL 허용)
    @Column(length = 50, unique = true)
    private String isbn;

    // 캐싱 당시 사용된 검색 키워드 (Gemini 생성 포함)
    @Column(name = "search_keyword", length = 500)
    private String searchKeyword;
}
