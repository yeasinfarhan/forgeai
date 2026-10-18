package com.forgeai.architecture;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ArchitectureRepository extends JpaRepository<Architecture, UUID> {
}
