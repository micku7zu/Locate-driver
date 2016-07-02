package com.micutu.locatedriver.Model;

public class LDPlace {
    private String name;
    private String id;
    private Double latitude;
    private Double longitude;

    public LDPlace(String name, String id, Double latitude, Double longitude) {
        this.name = name;
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
