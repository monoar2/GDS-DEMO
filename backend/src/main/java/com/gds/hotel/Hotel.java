package com.gds.hotel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "hotel", indexes = @Index(name = "ix_hotel_city", columnList = "city_code"))
public class Hotel {

    @Id
    private Long id;

    /** GDS property code: chain code + 4 digits, e.g. HI0042. */
    @Column(length = 8, nullable = false, unique = true)
    private String propertyCode;

    @Column(length = 2, nullable = false)
    private String chainCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "city_code", length = 3, nullable = false)
    private String cityCode;

    private int stars;
    private String address;

    protected Hotel() {
    }

    public Long getId() { return id; }
    public String getPropertyCode() { return propertyCode; }
    public String getChainCode() { return chainCode; }
    public String getName() { return name; }
    public String getCityCode() { return cityCode; }
    public int getStars() { return stars; }
    public String getAddress() { return address; }
}
