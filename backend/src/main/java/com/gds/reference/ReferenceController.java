package com.gds.reference;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gds.reference.ReferenceService.AirlineView;
import com.gds.reference.ReferenceService.AirportView;

@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final ReferenceService reference;

    public ReferenceController(ReferenceService reference) {
        this.reference = reference;
    }

    @GetMapping("/airports")
    public List<AirportView> airports() {
        return reference.airports();
    }

    @GetMapping("/airlines")
    public List<AirlineView> airlines() {
        return reference.airlines();
    }
}
