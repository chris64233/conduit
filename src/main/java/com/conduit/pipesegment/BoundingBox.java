package com.conduit.pipesegment;

public record BoundingBox(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude) {

    public BoundingBox {
        if (minLongitude >= maxLongitude || minLatitude >= maxLatitude) {
            throw new IllegalArgumentException("范围参数必须满足最小值小于最大值");
        }
    }

    public boolean intersects(PipeSegment segment) {
        double x1 = segment.getStartLongitude();
        double y1 = segment.getStartLatitude();
        double x2 = segment.getEndLongitude();
        double y2 = segment.getEndLatitude();

        if (Math.max(x1, x2) < minLongitude || Math.min(x1, x2) > maxLongitude
                || Math.max(y1, y2) < minLatitude || Math.min(y1, y2) > maxLatitude) {
            return false;
        }
        if (contains(x1, y1) || contains(x2, y2)) {
            return true;
        }
        return segmentsIntersect(x1, y1, x2, y2, minLongitude, minLatitude, maxLongitude, minLatitude)
                || segmentsIntersect(x1, y1, x2, y2, maxLongitude, minLatitude, maxLongitude, maxLatitude)
                || segmentsIntersect(x1, y1, x2, y2, maxLongitude, maxLatitude, minLongitude, maxLatitude)
                || segmentsIntersect(x1, y1, x2, y2, minLongitude, maxLatitude, minLongitude, minLatitude);
    }

    private boolean contains(double x, double y) {
        return x >= minLongitude && x <= maxLongitude && y >= minLatitude && y <= maxLatitude;
    }

    private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                             double cx, double cy, double dx, double dy) {
        double d1 = cross(cx, cy, dx, dy, ax, ay);
        double d2 = cross(cx, cy, dx, dy, bx, by);
        double d3 = cross(ax, ay, bx, by, cx, cy);
        double d4 = cross(ax, ay, bx, by, dx, dy);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        return (d1 == 0 && onSegment(cx, cy, dx, dy, ax, ay))
                || (d2 == 0 && onSegment(cx, cy, dx, dy, bx, by))
                || (d3 == 0 && onSegment(ax, ay, bx, by, cx, cy))
                || (d4 == 0 && onSegment(ax, ay, bx, by, dx, dy));
    }

    private static double cross(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static boolean onSegment(double ax, double ay, double bx, double by, double px, double py) {
        return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
                && py >= Math.min(ay, by) && py <= Math.max(ay, by);
    }
}
