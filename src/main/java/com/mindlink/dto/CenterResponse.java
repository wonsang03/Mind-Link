package com.mindlink.dto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CenterResponse {

    private final String id;
    private final String name;
    private final String title;
    private final List<String> specialty;
    private final String location;
    private final String phone;
    private final String description;
    private final String mapx;
    private final String mapy;
    /** API 원본 링크(블로그·카페 등) — 지도 보기에는 사용하지 않음 */
    private final String link;
    private final String imageUrl;
    /** 네이버 지도 검색/좌표 URL */
    private final String naverMapUrl;

    public CenterResponse(String id, String name, String title, List<String> specialty,
                          String location, String phone, String description,
                          String mapx, String mapy, String link) {
        this(id, name, title, specialty, location, phone, description, mapx, mapy, link, "");
    }

    public CenterResponse(String id, String name, String title, List<String> specialty,
                          String location, String phone, String description,
                          String mapx, String mapy, String link, String imageUrl) {
        this.id = id;
        this.name = name;
        this.title = title;
        this.specialty = specialty == null ? List.of() : specialty;
        this.location = location;
        this.phone = phone;
        this.description = description;
        this.mapx = mapx;
        this.mapy = mapy;
        this.link = link == null ? "" : link;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.naverMapUrl = buildNaverMapUrl(name, location, mapx, mapy);
    }

    public CenterResponse withImageUrl(String imageUrl) {
        return new CenterResponse(id, name, title, specialty, location, phone, description,
                mapx, mapy, link, imageUrl);
    }

    /**
     * 네이버 지역검색 mapx/mapy(WGS84 × 10⁷)가 있으면 해당 좌표로 지도를 연다.
     * 없으면 장소명만 검색한다. (이름+주소 조합은 지도 검색 정확도가 떨어져 사용하지 않음)
     */
    private static String buildNaverMapUrl(String name, String location, String mapx, String mapy) {
        Double lng = parseNaverCoord(mapx, true);
        Double lat = parseNaverCoord(mapy, false);
        if (lng != null && lat != null) {
            String title = name == null ? "" : name.trim();
            if (!title.isEmpty()) {
                String encoded = URLEncoder.encode(title, StandardCharsets.UTF_8);
                return String.format(
                        "https://map.naver.com/p/search/%s?c=%.7f,%.7f,17,0,0,0,dh",
                        encoded, lng, lat);
            }
            return String.format("https://map.naver.com/?lng=%.7f&lat=%.7f", lng, lat);
        }

        String query = name == null ? "" : name.trim();
        if (query.isEmpty() && location != null) {
            query = location.trim();
        }
        if (query.isEmpty()) {
            return "https://map.naver.com/";
        }
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "https://map.naver.com/p/search/" + encoded;
    }

    /** 네이버 지역검색 API: mapx/mapy는 WGS84 경·위도 × 10⁷ 정수 문자열 */
    private static Double parseNaverCoord(String raw, boolean longitude) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            if (raw.indexOf('.') < 0 && Math.abs(v) > 180) {
                v /= 10_000_000.0;
            }
            if (longitude) {
                if (v < -180 || v > 180) return null;
            } else {
                if (v < -90 || v > 90) return null;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public List<String> getSpecialty() { return specialty; }
    public String getLocation() { return location; }
    public String getPhone() { return phone; }
    public String getDescription() { return description; }
    public String getMapx() { return mapx; }
    public String getMapy() { return mapy; }
    public String getLink() { return link; }
    public String getImageUrl() { return imageUrl; }
    public String getNaverMapUrl() { return naverMapUrl; }
}
