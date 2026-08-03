package com.sashank.DigitalTwinBackend.repository;

import com.sashank.DigitalTwinBackend.model.Hospital;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    @Query(value = """
            SELECT h.id AS id,
                   h.name AS name,
                   h.type AS type,
                   h.address AS address,
                   ST_Y(h.geom) AS lat,
                   ST_X(h.geom) AS lon,
                   ST_Distance(h.geom::geography, s.geom::geography) / 1000.0 AS distanceKm
            FROM hospitals h, schools s
            WHERE s.id = :schoolId
              AND ST_DWithin(h.geom::geography, s.geom::geography, :withinKm * 1000)
            ORDER BY distanceKm
            """, nativeQuery = true)
    List<HospitalDistanceRow> findNearHospitalsForSchool(@Param("schoolId") Long schoolId, @Param("withinKm") double withinKm);
}