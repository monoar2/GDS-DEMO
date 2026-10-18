package com.gds.security;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.gds.agency.Agency;
import com.gds.agency.Agent;
import com.gds.config.GdsProperties;

@Service
public class JwtService {

    static final String ROLE_CLAIM = "roles";
    static final String AGENT_CLAIM = "agentId";
    static final String AGENCY_CLAIM = "agencyId";
    static final String PCC_CLAIM = "pcc";

    private final JwtEncoder encoder;
    private final GdsProperties props;

    public JwtService(JwtEncoder encoder, GdsProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public IssuedToken issue(Agent agent, Agency agency) {
        Instant now = Instant.now();
        Instant expires = now.plus(props.security().tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gds")
                .subject(agent.getUsername())
                .issuedAt(now)
                .expiresAt(expires)
                .claim(AGENT_CLAIM, agent.getId())
                .claim(AGENCY_CLAIM, agency.getId())
                .claim(PCC_CLAIM, agency.getPcc())
                .claim(ROLE_CLAIM, List.of(agent.getRole().name()))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expires);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
