package com.mindlink.recommendation;

/**
 * 감정 상태 → 네이버 검색키워드 + 기본 위로 멘트.
 */
public enum EmotionCategory {

    DEPRESSION("위로 감성 에세이",
            "지금 많이 힘드시겠어요. 따뜻한 위로가 되는 책들을 추천해드려요."),
    STRESS("스트레스 해소 마인드풀니스",
            "스트레스가 많이 쌓이셨군요. 마음을 편안하게 해주는 책들을 추천해드려요."),
    ANXIETY("불안 극복 마음챙김",
            "불안한 마음이 드시나요? 마음을 안정시키는 데 도움이 되는 책들을 추천해드려요."),
    LETHARGY("동기부여 자기계발",
            "의욕이 생기지 않으실 때, 다시 힘을 내게 해주는 책들을 추천해드려요."),
    RELATIONSHIP("인간관계 소통 공감",
            "관계 속에서 지친 마음을 보듬고 건강한 대화를 돕는 책들을 추천해드려요."),
    NORMAL("베스트셀러 교양",
            "현재 마음 상태가 안정적이에요. 다양한 분야를 넓혀갈 좋은 책들을 추천해드려요.");

    private final String searchQuery;
    private final String reason;

    EmotionCategory(String searchQuery, String reason) {
        this.searchQuery = searchQuery;
        this.reason = reason;
    }

    public String getSearchQuery() { return searchQuery; }
    public String getReason()      { return reason; }
}
