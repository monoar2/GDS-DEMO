package com.gds.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "airline")
public class Airline {

    @Id
    @Column(length = 2)
    private String code;

    private String name;

    @Column(length = 2)
    private String country;

    @Column(length = 3)
    private String hub;

    protected Airline() {
    }

    public Airline(String code, String name, String country, String hub) {
        this.code = code;
        this.name = name;
        this.country = country;
        this.hub = hub;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getHub() { return hub; }
}
