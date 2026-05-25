package com.mindlink.recommendation.dto;

import lombok.Getter;
import lombok.Setter;

/** 네이버 도서 검색 API 결과 단건. */
@Getter @Setter
public class NaverBookItem {
    private String title;
    private String link;
    private String image;
    private String author;
    private String publisher;
    private String description;
    private String isbn;  // "ISBN10 ISBN13" 형식 (공백 구분)
}
