package com.mindlink.dto;

/**
 * 커뮤니티 글/댓글 저장 결과 + 위기 키워드 모니터링 알림 생성 여부.
 */
public record CommunityWriteResult<T>(T payload, boolean crisisAlertCreated) {

    public static <T> CommunityWriteResult<T> of(T payload, boolean crisisAlertCreated) {
        return new CommunityWriteResult<>(payload, crisisAlertCreated);
    }
}
