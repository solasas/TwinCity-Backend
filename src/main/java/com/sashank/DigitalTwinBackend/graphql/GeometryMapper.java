package com.sashank.DigitalTwinBackend.graphql;

import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

final class GeometryMapper {

    private GeometryMapper() {
    }

    static List<GeoPoint> exteriorRing(Polygon polygon) {
        return Arrays.stream(polygon.getExteriorRing().getCoordinates())
                .map(c -> new GeoPoint(c.y, c.x))
                .toList();
    }

    static List<GeoPoint> line(LineString lineString) {
        return Arrays.stream(lineString.getCoordinates())
                .map(c -> new GeoPoint(c.y, c.x))
                .toList();
    }

    static GeoPoint point(Point point) {
        return new GeoPoint(point.getY(), point.getX());
    }
}