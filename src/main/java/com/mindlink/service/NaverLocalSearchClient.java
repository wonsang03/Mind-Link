package com.mindlink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindlink.config.NaverProperties;
import com.mindlink.dto.CenterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class NaverLocalSearchClient {

    private static final Logger log = LoggerFactory.getLogger(NaverLocalSearchClient.class);

    private static final Pattern COUNSELING_KEYWORD = Pattern.compile("상담|심리|정신건강|정신과");

    private final RestClient restClient;
    private final NaverProperties props;

    public NaverLocalSearchClient(RestClient restClient, NaverProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public List<CenterResponse> search(String query) {
        if (!props.isConfigured()) {
            return List.of();
        }
        String q = (query == null || query.isBlank()) ? props.getDefaultQuery() : query.trim();
        if (!COUNSELING_KEYWORD.matcher(q).find()) {
            q = q + " 상담";
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getLocalSearchUrl())
                .queryParam("query", q)
                .queryParam("display", props.getDisplay())
                .queryParam("start", 1)
                .queryParam("sort", "random")
                .build()
                .encode()
                .toUri();

        try {
            JsonNode body = restClient.get()
                    .uri(uri)
                    .header("X-Naver-Client-Id", props.getClientId())
                    .header("X-Naver-Client-Secret", props.getClientSecret())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.has("items") || !body.get("items").isArray()) {
                log.warn("Naver local search returned no items (query='{}')", q);
                return List.of();
            }
            List<CenterResponse> out = new ArrayList<>();
            int idx = 0;
            for (JsonNode item : body.get("items")) {
                if (!isCounselingCategory(text(item, "category"))) continue;
                out.add(toCenter(item, ++idx));
            }
            log.debug("Naver local search '{}' -> {} items (filtered)", q, out.size());
            return out;
        } catch (RestClientException e) {
            log.warn("Naver local search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private CenterResponse toCenter(JsonNode item, int idx) {
        String rawTitle = text(item, "title");
        String name = stripHtml(rawTitle);
        String category = text(item, "category");
        String title = lastCategorySegment(category);
        String phone = text(item, "telephone");
        String address = firstNonBlank(text(item, "roadAddress"), text(item, "address"));
        String description = stripHtml(text(item, "description"));
        String mapx = text(item, "mapx");
        String mapy = text(item, "mapy");
        String link = text(item, "link");
        return new CenterResponse(
                "naver-" + idx,
                name,
                title.isBlank() ? "상담 센터" : title,
                splitCategory(category),
                address,
                phone,
                description,
                mapx,
                mapy,
                link
        );
    }

    private static boolean isCounselingCategory(String category) {
        if (category == null || category.isBlank()) return false;
        return COUNSELING_KEYWORD.matcher(category).find();
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", "").trim();
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
