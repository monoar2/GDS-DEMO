package com.gds.agency;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findByUsername(String username);

    List<Agent> findByAgencyIdOrderByUsername(Long agencyId);

    boolean existsByUsername(String username);
}
