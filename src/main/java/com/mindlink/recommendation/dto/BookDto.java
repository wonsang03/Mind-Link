package com.mindlink.recommendation.dto;

import lombok.Getter;

import java.util.Locale;

/** 최종 응답 — 도서 1건. */
@Getter
public class BookDto {
    private final String title;
    private final String author;
    private final String publisher;
    private final String link;
    private final String image;
    private final String description;
    /** ISBN13 (없으면 빈 문자열). AI·네이버 캐시 시 DB 키로 사용 */
    private final String isbn;
    /**
     * 이 책에 대한 감정 태그(DEPRESSION 등). ③단계 Gemini가 권별로 판정하면 설정되고,
     * DB에서 내려줄 때는 해당 행의 emotion과 동일하게 둔다.
     */
    private final String bookEmotion;
    /** 평균 별점(리뷰 없으면 0)·리뷰 수 — 추천 페이지 인기순 정렬용 */
    private final double avgRating;
    private final int reviewCount;

    public BookDto(String title, String author, String publisher,
            String link, String image, String description) {
        this(title, author, publisher, link, image, description, "", "");
    }

    public BookDto(String title, String author, String publisher,
            String link, String image, String description, String isbn, String bookEmotion) {
        this(title, author, publisher, link, image, description, isbn, bookEmotion, 0.0, 0);
    }

    public BookDto(String title, String author, String publisher,
            String link, String image, String description, String isbn, String bookEmotion,
            double avgRating, int reviewCount) {
        this.title       = strip(title);
        this.author      = strip(author);
        this.publisher   = publisher   == null ? "" : publisher;
        this.link        = link        == null ? "" : link;
        this.image       = image       == null ? "" : image;
        this.description = strip(description);
        this.isbn        = isbn == null ? "" : isbn.trim();
        this.bookEmotion = bookEmotion == null || bookEmotion.isBlank() ? "" : bookEmotion.trim().toUpperCase(Locale.ROOT);
        this.avgRating   = avgRating;
        this.reviewCount = reviewCount;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").trim();
    }
}
