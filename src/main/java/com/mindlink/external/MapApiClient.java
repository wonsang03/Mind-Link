package com.mindlink.external;

import com.mindlink.dto.CounselingCenterResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 지도/공공데이터 API 호출 클라이언트.
 * API Key 설정 후 실제 호출 로직을 구현하세요.
 *
 * application.properties 또는 환경변수 설정:
 *   map.api.key=YOUR_API_KEY
 *   map.api.url=https://api.example.com/counseling-centers
 *
 * 공공데이터 포털(data.go.kr) 정신건강 상담소 API 연동을 권장합니다.
 */
@Component
public class MapApiClient {

    @Value("${map.api.key:MAP_KEY_NOT_SET}")
    private String apiKey;

    @Value("${map.api.url:https://api.example.com/counseling-centers}")
    private String apiUrl;

    /**
     * 위도/경도 기반으로 주변 상담소 목록을 조회합니다.
     * TODO: 실제 API 호출 로직을 구현하세요.
     */
    public List<CounselingCenterResponse> findNearby(double latitude, double longitude, int radius) {
        if ("MAP_KEY_NOT_SET".equals(apiKey)) {
            // API Key 미설정 시 더미 데이터 반환
            return List.of(
                    new CounselingCenterResponse("서울 정신건강복지센터", "서울시 중구 세종대로 110",
                            "02-3700-7000", "국공립", "37.5665", "126.9780"),
                    new CounselingCenterResponse("마음이음 상담센터", "서울시 강남구 테헤란로 123",
                            "02-1234-5678", "민간", "37.5012", "127.0396")
            );
        }
        // TODO: 실제 API 호출 구현
        throw new UnsupportedOperationException("지도 API 호출이 아직 구현되지 않았습니다.");
    }
}
