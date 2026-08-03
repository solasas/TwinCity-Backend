package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.model.Road;
import com.sashank.DigitalTwinBackend.repository.RoadRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class RoadController {

    private final RoadRepository roadRepository;

    public RoadController(RoadRepository roadRepository) {
        this.roadRepository = roadRepository;
    }

    @SchemaMapping(typeName = "Road", field = "geometry")
    List<GeoPoint> geometry(Road road) {
        return GeometryMapper.line(road.getGeom());
    }

    @MutationMapping
    @Transactional
    Road simulateRoadClosure(@Argument Long roadId) {
        Road road = roadRepository.findById(roadId)
                .orElseThrow(() -> new NoSuchElementException("No road with id " + roadId));
        road.setOpen(!road.isOpen());
        return roadRepository.save(road);
    }
}