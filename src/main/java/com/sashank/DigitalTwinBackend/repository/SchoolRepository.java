package com.sashank.DigitalTwinBackend.repository;

import com.sashank.DigitalTwinBackend.model.School;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolRepository extends JpaRepository<School, Long> {

    @Query(value = """
            SELECT s.*
            FROM schools s
            ORDER BY s.geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
            LIMIT 1
            """, nativeQuery = true)
    Optional<School> findNearestTo(@Param("lat") double lat, @Param("lon") double lon);
}
