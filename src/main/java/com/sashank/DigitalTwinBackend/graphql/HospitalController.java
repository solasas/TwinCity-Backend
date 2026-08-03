package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.model.Hospital;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class HospitalController {

    @SchemaMapping(typeName = "Hospital", field = "location")
    GeoPoint location(Hospital hospital) {
        return GeometryMapper.point(hospital.getGeom());
    }
}