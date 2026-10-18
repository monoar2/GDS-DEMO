package com.gds.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "airport")
public class Airport {

    @Id
    @Column(length = 3)
    private String code;

    private String name;
    private String city;

    @Column(length = 2)
    private String country;

    private String timezone;
    private double latitude;
    private double longitude;

    protected Airport() {
    }

    public Airport(String code, String name, String city, String country, String timezone, double latitude, double longitude) {
        this.code = code;
        this.name = name;
        this.city = city;
        this.country = country;
        this.timezone = timezone;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getTimezone() { return timezone; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
