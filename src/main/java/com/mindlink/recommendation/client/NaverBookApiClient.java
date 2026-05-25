package com.mindlink.recommendation.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindlink.recommendation.dto.NaverBookItem;
import com.mindlink.recommendation.dto.NaverBookSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** 네이버 도서 검색 API 클라이언트 (DB 결과 없을 때 fallback). */
@Component
public class NaverBookApiClient {

    private static final String API_URL = "https://openapi.naver.com/v1/search/book.json";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${naver.api.client-id:NOT_SET}")
    private String clientId;

    @Value("${naver.api.client-secret:NOT_SET}")
    private String clientSecret;

    public List<NaverBookItem> search(String query, int size) {
        return search(query, size, 1, "sim");
    }

    /**
     * @param start 네이버 API start (1 기반, 최대 1000)
     * @param sort  sim(정확도순) | date(출간일) | count(판매순)
     */
    public List<NaverBookItem> search(String query, int display, int start, String sort) {
        if ("NOT_SET".equals(clientId) || "NOT_SET".equals(clientSecret)) return List.of();
        try {
            int disp = Math.min(100, Math.max(1, display));
            int st = Math.max(1, Math.min(start, 1000));
            String srt = sort == null || sort.isBlank() ? "sim" : sort.trim().toLowerCase(Locale.ROOT);
            if (!"sim".equals(srt) && !"date".equals(srt) && !"count".equals(srt)) {
                srt = "sim";
            }
            String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = API_URL + "?query=" + enc + "&display=" + disp + "&start=" + st + "&sort=" + srt;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Naver-Client-Id",     clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();
            NaverBookSearchResponse parsed = MAPPER.readValue(resp.body(), NaverBookSearchResponse.class);
            return parsed.getItems() != null ? parsed.getItems() : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
