package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.gtfs.Vehicle;
import com.sashank.DigitalTwinBackend.gtfs.VehicleStore;
import java.util.List;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class VehicleController {

    private final VehicleStore vehicleStore;

    public VehicleController(VehicleStore vehicleStore) {
        this.vehicleStore = vehicleStore;
    }

    @QueryMapping
    List<Vehicle> vehicles() {
        return vehicleStore.findAll();
    }
}