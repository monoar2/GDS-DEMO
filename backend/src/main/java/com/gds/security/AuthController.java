package com.gds.security;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gds.agency.Agency;
import com.gds.agency.AgencyRepository;
import com.gds.agency.Agent;
import com.gds.agency.AgentRepository;
import com.gds.agency.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AgentRepository agents;
    private final AgencyRepository agencies;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(AgentRepository agents, AgencyRepository agencies, PasswordEncoder encoder, JwtService jwt) {
        this.agents = agents;
        this.agencies = agencies;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Agent agent = agents.findByUsername(request.username())
                .filter(Agent::isActive)
                .filter(a -> encoder.matches(request.password(), a.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password"));
        Agency agency = agencies.findById(agent.getAgencyId())
                .filter(Agency::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Agency is not active"));
        var token = jwt.issue(agent, agency);
        return new LoginResponse(token.token(), token.expiresAt(), agent.getUsername(), agent.getFullName(),
                agent.getRole(), agency.getPcc(), agency.getName());
    }

    @GetMapping("/me")
    public CurrentAgent me(@AuthenticationPrincipal Jwt token) {
        return CurrentAgent.from(token);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, String username, String fullName, Role role,
                                String pcc, String agencyName) {
    }
}
