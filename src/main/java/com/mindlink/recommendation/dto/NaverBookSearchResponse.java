package com.mindlink.recommendation.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 네이버 도서 검색 API 응답 역직렬화용. */
@Getter @Setter
public class NaverBookSearchResponse {
    private List<NaverBookItem> items;
}
