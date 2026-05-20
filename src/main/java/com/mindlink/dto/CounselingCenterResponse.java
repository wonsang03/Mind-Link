package com.mindlink.dto;

public class CounselingCenterResponse {

    private String name;
    private String address;
    private String phone;
    private String type;      // 국공립, 민간, 온라인 등
    private String latitude;
    private String longitude;

    public CounselingCenterResponse() {}

    public CounselingCenterResponse(String name, String address, String phone,
                                     String type, String latitude, String longitude) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getType() { return type; }
    public String getLatitude() { return latitude; }
    public String getLongitude() { return longitude; }

    public void setName(String name) { this.name = name; }
    public void setAddress(String address) { this.address = address; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setType(String type) { this.type = type; }
    public void setLatitude(String latitude) { this.latitude = latitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }
}
