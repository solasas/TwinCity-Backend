package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.gtfs.Vehicle;
import com.sashank.DigitalTwinBackend.gtfs.VehicleStore;
import com.sashank.DigitalTwinBackend.realtime.VehicleUpdatePublisher;
import java.util.List;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class VehicleController {

    private final VehicleStore vehicleStore;
    private final VehicleUpdatePublisher vehicleUpdatePublisher;

    public VehicleController(VehicleStore vehicleStore, VehicleUpdatePublisher vehicleUpdatePublisher) {
        this.vehicleStore = vehicleStore;
        this.vehicleUpdatePublisher = vehicleUpdatePublisher;
    }

    @QueryMapping
    List<Vehicle> vehicles() {
        return vehicleStore.findAll();
    }

    @SubscriptionMapping
    Flux<Vehicle> vehicleUpdates() {
        return vehicleUpdatePublisher.updates();
    }
}