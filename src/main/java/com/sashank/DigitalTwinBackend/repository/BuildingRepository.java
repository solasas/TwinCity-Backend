package com.sashank.DigitalTwinBackend.repository;

import com.sashank.DigitalTwinBackend.model.Building;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Long> {
}