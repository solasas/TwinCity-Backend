package com.sashank.DigitalTwinBackend.cache;

import com.sashank.DigitalTwinBackend.model.Road;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/**
 * JSON-cacheable stand-in for {@link Road}: the entity's {@code geom} is a JTS {@link LineString},
 * which isn't directly Jackson-serializable, so it's round-tripped through WKT instead.
 */
public record RoadCacheEntry(Long id, Long osmId, String name, String type, boolean open, String geomWkt) {

    private static final int WGS84_SRID = 4326;

    public static RoadCacheEntry from(Road road) {
        return new RoadCacheEntry(road.getId(), road.getOsmId(), road.getName(), road.getType(), road.isOpen(),
                new WKTWriter().write(road.getGeom()));
    }

    public Road toRoad() {
        Road road = new Road();
        road.setId(id);
        road.setOsmId(osmId);
        road.setName(name);
        road.setType(type);
        road.setOpen(open);
        try {
            GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), WGS84_SRID);
            road.setGeom((LineString) new WKTReader(geometryFactory).read(geomWkt));
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to parse cached road geometry: " + geomWkt, e);
        }
        return road;
    }
}