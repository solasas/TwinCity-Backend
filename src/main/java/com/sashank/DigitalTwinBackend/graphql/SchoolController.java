package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.cache.QueryCache;
import com.sashank.DigitalTwinBackend.cache.RoadCacheEntry;
import com.sashank.DigitalTwinBackend.model.Road;
import com.sashank.DigitalTwinBackend.model.School;
import com.sashank.DigitalTwinBackend.repository.HospitalRepository;
import com.sashank.DigitalTwinBackend.repository.RoadRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import tools.jackson.core.type.TypeReference;

@Controller
public class SchoolController {

    /**
     * The PostGIS distance queries backing these compound lookups are the expensive part of
     * resolving a school; a short TTL keeps results fresh (e.g. after a road closure) while still
     * absorbing bursts of repeat queries.
     */
    private static final Duration NEARBY_QUERY_CACHE_TTL = Duration.ofSeconds(30);

    private final HospitalRepository hospitalRepository;
    private final RoadRepository roadRepository;
    private final QueryCache queryCache;

    public SchoolController(HospitalRepository hospitalRepository, RoadRepository roadRepository,
            QueryCache queryCache) {
        this.hospitalRepository = hospitalRepository;
        this.roadRepository = roadRepository;
        this.queryCache = queryCache;
    }

    @SchemaMapping(typeName = "School", field = "location")
    GeoPoint location(School school) {
        return GeometryMapper.point(school.getGeom());
    }

    @SchemaMapping(typeName = "School", field = "nearbyHospitals")
    List<HospitalDistance> nearbyHospitals(School school, @Argument double withinKm) {
        String cacheKey = "nearbyHospitals:%d:%s".formatted(school.getId(), withinKm);
        return queryCache.getOrCompute(cacheKey, NEARBY_QUERY_CACHE_TTL, new TypeReference<>() {},
                () -> hospitalRepository.findNearHospitalsForSchool(school.getId(), withinKm).stream()
                        .map(row -> new HospitalDistance(
                                row.getId(),
                                row.getName(),
                                row.getType(),
                                row.getAddress(),
                                new GeoPoint(row.getLat(), row.getLon()),
                                row.getDistanceKm()))
                        .toList());
    }

    @SchemaMapping(typeName = "School", field = "nearbyRoads")
    List<Road> nearbyRoads(School school, @Argument double withinMeters) {
        String cacheKey = "nearbyRoads:%d:%s".formatted(school.getId(), withinMeters);
        List<RoadCacheEntry> entries = queryCache.getOrCompute(cacheKey, NEARBY_QUERY_CACHE_TTL, new TypeReference<>() {},
                () -> {
                    GeoPoint location = GeometryMapper.point(school.getGeom());
                    return roadRepository.findOpenRoadsNear(location.lat(), location.lon(), withinMeters).stream()
                            .map(RoadCacheEntry::from)
                            .toList();
                });
        return entries.stream().map(RoadCacheEntry::toRoad).toList();
    }
}