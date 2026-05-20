package com.mindlink.dto;

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
    private final String link;

    public CenterResponse(String id, String name, String title, List<String> specialty,
                          String location, String phone, String description,
                          String mapx, String mapy, String link) {
        this.id = id;
        this.name = name;
        this.title = title;
        this.specialty = specialty == null ? List.of() : specialty;
        this.location = location;
        this.phone = phone;
        this.description = description;
        this.mapx = mapx;
        this.mapy = mapy;
        this.link = link;
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
}
