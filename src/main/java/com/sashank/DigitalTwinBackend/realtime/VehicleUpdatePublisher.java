package com.sashank.DigitalTwinBackend.realtime;

import com.sashank.DigitalTwinBackend.gtfs.Vehicle;
import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Bridges vehicle-change events (received from Redis pub/sub, see {@link VehicleUpdateRedisListener})
 * into a hot {@link Flux} that the {@code vehicleUpdates} GraphQL subscription taps into.
 *
 * <p>Uses a best-effort direct sink rather than a buffering one: this is a live position feed, not
 * an event log, so when there are no (or slow) subscribers — e.g. between client (re)connects — it's
 * correct to drop updates rather than buffer them indefinitely. Clients get a fresh snapshot from the
 * {@code vehicles} query on (re)connect.
 */
@Component
public class VehicleUpdatePublisher {

    private final Sinks.Many<Vehicle> sink = Sinks.many().multicast().directBestEffort();

    /**
     * {@code RedisMessageListenerContainer} dispatches pub/sub messages across a pool of threads, so
     * calls here can be concurrent. Reactor sinks require emissions to be serialized (Reactive Streams
     * rule 1.3); {@code synchronized} guarantees that regardless of how many listener threads call in.
     */
    public synchronized void publish(Vehicle vehicle) {
        sink.emitNext(vehicle, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
    }

    public Flux<Vehicle> updates() {
        return sink.asFlux();
    }
}