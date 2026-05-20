package com.mindlink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindlink.config.NaverProperties;
import com.mindlink.dto.CenterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class NaverLocalSearchClient {

    private static final Logger log = LoggerFactory.getLogger(NaverLocalSearchClient.class);

    private static final Pattern COUNSELING_HINT =
            Pattern.compile("상담|심리|정신|건강|복지|상담소|상담센터|의원|병원|클리닉");

    private static final String IMAGE_SEARCH_URL = "https://openapi.naver.com/v1/search/image.json";
    /** 이미지 API는 검색 속도에 영향 → 상위 N건만 */
    private static final int MAX_IMAGE_LOOKUPS = 6;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NaverProperties props;

    public NaverLocalSearchClient(NaverProperties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public List<CenterResponse> search(String query) {
        if (!props.isConfigured()) {
            return List.of();
        }

        String q = normalizeQuery(query);
        URI uri = UriComponentsBuilder.fromUriString(props.getLocalSearchUrl())
                .queryParam("query", q)
                .queryParam("display", props.getDisplay())
                .queryParam("start", 1)
                .queryParam("sort", "comment")
                .build()
                .encode()
                .toUri();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("X-Naver-Client-Id", props.getClientId())
                    .header("X-Naver-Client-Secret", props.getClientSecret())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Naver local search HTTP {} (query='{}'): {}", response.statusCode(), q, response.body());
                return List.of();
            }

            JsonNode body = MAPPER.readTree(response.body());
            if (body.has("errorCode") || body.has("errorMessage")) {
                log.warn("Naver local search API error (query='{}'): {} {}",
                        q, text(body, "errorCode"), text(body, "errorMessage"));
                return List.of();
            }
            if (!body.has("items") || !body.get("items").isArray() || body.get("items").isEmpty()) {
                log.warn("Naver local search empty (query='{}', total={})", q, text(body, "total"));
                return List.of();
            }

            List<CenterResponse> raw = new ArrayList<>();
            int idx = 0;
            for (JsonNode item : body.get("items")) {
                raw.add(toCenter(item, ++idx));
            }

            List<CenterResponse> out = preferCounselingRelated(raw);
            enrichImages(out);
            log.info("Naver local search '{}' -> api {} / shown {}", q, raw.size(), out.size());
            return out;
        } catch (Exception e) {
            log.warn("Naver local search failed (query='{}'): {}", q, e.getMessage());
            return List.of();
        }
    }

    /** CounselingService 와 동일 규칙 */
    public String normalizeQuery(String query) {
        String q = (query == null || query.isBlank()) ? props.getDefaultQuery() : query.trim();
        q = stripHtml(q);
        if (!COUNSELING_HINT.matcher(q).find()) {
            return q + " 심리상담센터";
        }
        return q;
    }

    /**
     * 상담 관련 항목 우선. 필터 후 0건이면 API 원본 그대로 반환(검색어가 이미 좁혀져 있음).
     */
    private List<CenterResponse> preferCounselingRelated(List<CenterResponse> raw) {
        List<CenterResponse> matched = new ArrayList<>();
        for (CenterResponse c : raw) {
            if (isCounselingRelated(c)) {
                matched.add(c);
            }
        }
        if (!matched.isEmpty()) {
            return matched;
        }
        return new ArrayList<>(raw);
    }

    private static boolean isCounselingRelated(CenterResponse c) {
        String blob = (c.getName() + " " + c.getTitle() + " " + c.getLocation() + " "
                + String.join(" ", c.getSpecialty()) + " " + c.getDescription()).toLowerCase();
        return COUNSELING_HINT.matcher(blob).find();
    }

    private CenterResponse toCenter(JsonNode item, int idx) {
        String name = stripHtml(text(item, "title"));
        String category = text(item, "category");
        String title = lastCategorySegment(category);
        String phone = text(item, "telephone");
        String address = firstNonBlank(text(item, "roadAddress"), text(item, "address"));
        String description = stripHtml(text(item, "description"));
        return new CenterResponse(
                "naver-" + idx,
                name,
                title.isBlank() ? "상담 센터" : title,
                splitCategory(category),
                address,
                phone,
                description,
                text(item, "mapx"),
                text(item, "mapy"),
                text(item, "link")
        );
    }

    private void enrichImages(List<CenterResponse> centers) {
        int limit = Math.min(centers.size(), MAX_IMAGE_LOOKUPS);
        for (int i = 0; i < limit; i++) {
            CenterResponse c = centers.get(i);
            String image = fetchImageThumbnail(c.getName());
            if (!image.isBlank()) {
                centers.set(i, c.withImageUrl(image));
            }
        }
    }

    private String fetchImageThumbnail(String centerName) {
        if (centerName == null || centerName.isBlank()) return "";
        String q = stripHtml(centerName);
        URI uri = UriComponentsBuilder.fromUriString(IMAGE_SEARCH_URL)
                .queryParam("query", q)
                .queryParam("display", 1)
                .queryParam("sort", "sim")
                .build()
                .encode()
                .toUri();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Naver-Client-Id", props.getClientId())
                    .header("X-Naver-Client-Secret", props.getClientSecret())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return "";
            JsonNode body = MAPPER.readTree(response.body());
            if (!body.has("items") || !body.get("items").isArray() || body.get("items").isEmpty()) {
                return "";
            }
            String thumb = text(body.get("items").get(0), "thumbnail");
            return thumb.isBlank() ? text(body.get("items").get(0), "link") : thumb;
        } catch (Exception e) {
            log.debug("Naver image search skipped for '{}': {}", centerName, e.getMessage());
            return "";
        }
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    private static String lastCategorySegment(String category) {
        if (category == null || category.isBlank()) return "";
        String[] parts = category.split(">");
        return parts[parts.length - 1].trim();
    }

    private static List<String> splitCategory(String category) {
        if (category == null || category.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : category.split("[>,]")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
