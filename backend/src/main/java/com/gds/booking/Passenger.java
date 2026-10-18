package com.gds.booking;

import java.time.LocalDate;

import com.gds.pricing.PassengerType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "pnr_passenger")
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "passenger_seq")
    @SequenceGenerator(name = "passenger_seq", sequenceName = "passenger_seq", allocationSize = 50)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 3, nullable = false)
    private PassengerType type;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate dateOfBirth;

    protected Passenger() {
    }

    public Passenger(PassengerType type, String firstName, String lastName, LocalDate dateOfBirth) {
        this.type = type;
        this.firstName = firstName.toUpperCase();
        this.lastName = lastName.toUpperCase();
        this.dateOfBirth = dateOfBirth;
    }

    public Long getId() { return id; }
    public PassengerType getType() { return type; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }

    /** GDS name format, e.g. SMITH/JOHN. */
    public String displayName() {
        return lastName + "/" + firstName;
    }
}
