package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.model.Building;
import com.sashank.DigitalTwinBackend.model.Hospital;
import com.sashank.DigitalTwinBackend.model.Road;
import com.sashank.DigitalTwinBackend.model.School;
import com.sashank.DigitalTwinBackend.repository.BuildingRepository;
import com.sashank.DigitalTwinBackend.repository.HospitalRepository;
import com.sashank.DigitalTwinBackend.repository.RoadRepository;
import com.sashank.DigitalTwinBackend.repository.SchoolRepository;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class QueryController {

    private final BuildingRepository buildingRepository;
    private final RoadRepository roadRepository;
    private final SchoolRepository schoolRepository;
    private final HospitalRepository hospitalRepository;

    public QueryController(BuildingRepository buildingRepository, RoadRepository roadRepository,
            SchoolRepository schoolRepository, HospitalRepository hospitalRepository) {
        this.buildingRepository = buildingRepository;
        this.roadRepository = roadRepository;
        this.schoolRepository = schoolRepository;
        this.hospitalRepository = hospitalRepository;
    }

    @QueryMapping
    List<Building> buildings() {
        return buildingRepository.findAll();
    }

    @QueryMapping
    List<Road> roads() {
        return roadRepository.findAll();
    }

    @QueryMapping
    List<School> schools() {
        return schoolRepository.findAll();
    }

    @QueryMapping
    List<Hospital> hospitals() {
        return hospitalRepository.findAll();
    }

    @QueryMapping
    School school(@Argument Long id) {
        return schoolRepository.findById(id).orElse(null);
    }

    @QueryMapping
    Hospital hospital(@Argument Long id) {
        return hospitalRepository.findById(id).orElse(null);
    }

    @QueryMapping
    School nearestSchool(@Argument double lat, @Argument double lon) {
        return schoolRepository.findNearestTo(lat, lon).orElse(null);
    }
}