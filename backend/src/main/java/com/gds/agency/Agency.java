package com.gds.agency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A travel agency subscribed to the GDS, identified by its pseudo city code (PCC). */
@Entity
@Table(name = "agency")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 9, nullable = false, unique = true)
    private String pcc;

    @Column(nullable = false)
    private String name;

    @Column(length = 2, nullable = false)
    private String country;

    private boolean active = true;

    protected Agency() {
    }

    public Agency(String pcc, String name, String country) {
        this.pcc = pcc;
        this.name = name;
        this.country = country;
    }

    public Long getId() { return id; }
    public String getPcc() { return pcc; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }
}
