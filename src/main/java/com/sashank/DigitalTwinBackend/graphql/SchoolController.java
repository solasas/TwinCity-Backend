package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.model.Road;
import com.sashank.DigitalTwinBackend.model.School;
import com.sashank.DigitalTwinBackend.repository.HospitalRepository;
import com.sashank.DigitalTwinBackend.repository.RoadRepository;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class SchoolController {

    private final HospitalRepository hospitalRepository;
    private final RoadRepository roadRepository;

    public SchoolController(HospitalRepository hospitalRepository, RoadRepository roadRepository) {
        this.hospitalRepository = hospitalRepository;
        this.roadRepository = roadRepository;
    }

    @SchemaMapping(typeName = "School", field = "location")
    GeoPoint location(School school) {
        return GeometryMapper.point(school.getGeom());
    }

    @SchemaMapping(typeName = "School", field = "nearbyHospitals")
    List<HospitalDistance> nearbyHospitals(School school, @Argument double withinKm) {
        return hospitalRepository.findNearHospitalsForSchool(school.getId(), withinKm).stream()
                .map(row -> new HospitalDistance(
                        row.getId(),
                        row.getName(),
                        row.getType(),
                        row.getAddress(),
                        new GeoPoint(row.getLat(), row.getLon()),
                        row.getDistanceKm()))
                .toList();
    }

    @SchemaMapping(typeName = "School", field = "nearbyRoads")
    List<Road> nearbyRoads(School school, @Argument double withinMeters) {
        GeoPoint location = GeometryMapper.point(school.getGeom());
        return roadRepository.findOpenRoadsNear(location.lat(), location.lon(), withinMeters);
    }
}