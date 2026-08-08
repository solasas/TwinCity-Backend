package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.model.Hospital;
import com.sashank.DigitalTwinBackend.repository.HospitalRepository;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class HospitalController {

    private final HospitalRepository hospitalRepository;

    public HospitalController(HospitalRepository hospitalRepository) {
        this.hospitalRepository = hospitalRepository;
    }

    @SchemaMapping(typeName = "Hospital", field = "location")
    GeoPoint location(Hospital hospital) {
        return GeometryMapper.point(hospital.getGeom());
    }

    @QueryMapping
    List<HospitalDistance> hospitalsNear(@Argument double lat, @Argument double lng, @Argument double radiusKm) {
        return hospitalRepository.findNearPoint(lat, lng, radiusKm).stream()
                .map(row -> new HospitalDistance(
                        row.getId(),
                        row.getName(),
                        row.getType(),
                        row.getAddress(),
                        new GeoPoint(row.getLat(), row.getLon()),
                        row.getDistanceKm()))
                .toList();
    }
}