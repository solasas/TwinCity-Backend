package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.congestion.RoadCongestionHeuristic;
import com.sashank.DigitalTwinBackend.gtfs.VehicleStore;
import com.sashank.DigitalTwinBackend.model.Road;
import com.sashank.DigitalTwinBackend.repository.RoadRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class RoadController {

    private final RoadRepository roadRepository;
    private final VehicleStore vehicleStore;
    private final RoadCongestionHeuristic congestionHeuristic;

    public RoadController(RoadRepository roadRepository, VehicleStore vehicleStore,
            RoadCongestionHeuristic congestionHeuristic) {
        this.roadRepository = roadRepository;
        this.vehicleStore = vehicleStore;
        this.congestionHeuristic = congestionHeuristic;
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

    @QueryMapping
    String congestionLevel(@Argument Long roadId) {
        Road road = roadRepository.findById(roadId)
                .orElseThrow(() -> new NoSuchElementException("No road with id " + roadId));
        return congestionHeuristic.estimate(road, vehicleStore.findAll()).name();
    }
}