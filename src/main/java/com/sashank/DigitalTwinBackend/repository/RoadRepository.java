package com.sashank.DigitalTwinBackend.repository;

import com.sashank.DigitalTwinBackend.model.Road;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadRepository extends JpaRepository<Road, Long> {

    @Query(value = """
            SELECT r.*
            FROM roads r
            WHERE r.open = true
              AND ST_DWithin(
                    r.geom::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :meters)
            ORDER BY ST_Distance(
                    r.geom::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
            """, nativeQuery = true)
    List<Road> findOpenRoadsNear(@Param("lat") double lat, @Param("lon") double lon, @Param("meters") double meters);
}