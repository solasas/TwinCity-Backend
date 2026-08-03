package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.model.Building;
import java.util.List;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class BuildingController {

    @SchemaMapping(typeName = "Building", field = "geometry")
    List<GeoPoint> geometry(Building building) {
        return GeometryMapper.exteriorRing(building.getGeom());
    }
}