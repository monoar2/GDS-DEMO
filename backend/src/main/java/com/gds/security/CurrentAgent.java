package com.gds.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.gds.agency.Role;

/** The agent behind a request, read from the JWT so no database lookup is needed per call. */
public record CurrentAgent(Long agentId, String username, Long agencyId, String pcc, Role role) {

    public static CurrentAgent from(Jwt jwt) {
        return new CurrentAgent(
                ((Number) jwt.getClaim(JwtService.AGENT_CLAIM)).longValue(),
                jwt.getSubject(),
                ((Number) jwt.getClaim(JwtService.AGENCY_CLAIM)).longValue(),
                jwt.getClaimAsString(JwtService.PCC_CLAIM),
                Role.valueOf(jwt.getClaimAsStringList(JwtService.ROLE_CLAIM).getFirst()));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Agents and supervisors only see their own agency's bookings; admins see everything. */
    public boolean canAccessAgency(Long otherAgencyId) {
        return isAdmin() || agencyId.equals(otherAgencyId);
    }
}
