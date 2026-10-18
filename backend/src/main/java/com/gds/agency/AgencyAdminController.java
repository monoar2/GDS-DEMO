package com.gds.agency;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gds.common.ConflictException;
import com.gds.common.NotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AgencyAdminController {

    private final AgencyRepository agencies;
    private final AgentRepository agents;
    private final PasswordEncoder encoder;

    public AgencyAdminController(AgencyRepository agencies, AgentRepository agents, PasswordEncoder encoder) {
        this.agencies = agencies;
        this.agents = agents;
        this.encoder = encoder;
    }

    @GetMapping("/agencies")
    public List<AgencyView> agencies() {
        return agencies.findAll().stream().map(AgencyView::of).toList();
    }

    @PostMapping("/agencies")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AgencyView createAgency(@Valid @RequestBody NewAgency request) {
        if (agencies.findByPcc(request.pcc()).isPresent()) {
            throw new ConflictException("PCC " + request.pcc() + " is already taken");
        }
        return AgencyView.of(agencies.save(new Agency(request.pcc(), request.name(), request.country())));
    }

    @PatchMapping("/agencies/{id}")
    @Transactional
    public AgencyView setAgencyActive(@PathVariable Long id, @RequestBody ActiveFlag flag) {
        Agency agency = agencies.findById(id).orElseThrow(() -> new NotFoundException("No agency " + id));
        agency.setActive(flag.active());
        return AgencyView.of(agency);
    }

    @GetMapping("/agencies/{id}/agents")
    public List<AgentView> agents(@PathVariable Long id) {
        return agents.findByAgencyIdOrderByUsername(id).stream().map(AgentView::of).toList();
    }

    @PostMapping("/agents")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AgentView createAgent(@Valid @RequestBody NewAgent request) {
        if (!agencies.existsById(request.agencyId())) {
            throw new NotFoundException("No agency " + request.agencyId());
        }
        if (agents.existsByUsername(request.username())) {
            throw new ConflictException("Username " + request.username() + " is already taken");
        }
        return AgentView.of(agents.save(new Agent(request.username(), encoder.encode(request.password()),
                request.fullName(), request.role(), request.agencyId())));
    }

    @PatchMapping("/agents/{id}")
    @Transactional
    public AgentView setAgentActive(@PathVariable Long id, @RequestBody ActiveFlag flag) {
        Agent agent = agents.findById(id).orElseThrow(() -> new NotFoundException("No agent " + id));
        agent.setActive(flag.active());
        return AgentView.of(agent);
    }

    public record NewAgency(@NotBlank @Pattern(regexp = "[A-Z0-9]{4,9}") String pcc, @NotBlank String name,
                            @NotBlank @Pattern(regexp = "[A-Z]{2}") String country) {
    }

    public record NewAgent(@NotBlank @Size(max = 64) String username, @NotBlank @Size(min = 6) String password,
                           @NotBlank String fullName, @NotNull Role role, @NotNull Long agencyId) {
    }

    public record ActiveFlag(boolean active) {
    }

    public record AgencyView(Long id, String pcc, String name, String country, boolean active) {
        static AgencyView of(Agency a) {
            return new AgencyView(a.getId(), a.getPcc(), a.getName(), a.getCountry(), a.isActive());
        }
    }

    public record AgentView(Long id, String username, String fullName, Role role, Long agencyId, boolean active) {
        static AgentView of(Agent a) {
            return new AgentView(a.getId(), a.getUsername(), a.getFullName(), a.getRole(), a.getAgencyId(), a.isActive());
        }
    }
}
