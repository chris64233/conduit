package com.conduit.pipesegment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundingBoxTest {

    private final BoundingBox box = new BoundingBox(10, 10, 20, 20);

    private PipeSegment segment(double x1, double y1, double x2, double y2) {
        return new PipeSegment("C", "C", "n", UtilityType.WATER, "m", 100, x1, y1, x2, y2,
                PipeSegmentStatus.ACTIVE);
    }

    @Test
    void matchesSegmentInsideBox() {
        assertThat(box.intersects(segment(12, 12, 14, 14))).isTrue();
    }

    @Test
    void matchesSegmentCrossingBox() {
        assertThat(box.intersects(segment(0, 0, 30, 30))).isTrue();
        assertThat(box.intersects(segment(5, 15, 25, 15))).isTrue();
    }

    @Test
    void matchesSegmentTouchingBoundary() {
        assertThat(box.intersects(segment(0, 10, 10, 10))).isTrue();
    }

    @Test
    void rejectsDisjointSegment() {
        assertThat(box.intersects(segment(0, 0, 5, 5))).isFalse();
        assertThat(box.intersects(segment(21, 15, 30, 15))).isFalse();
    }

    @Test
    void rejectsSegmentWhoseBoundingBoxOverlapsButLineDoesNot() {
        // 线段从 (9, 10.5) 到 (10.5, 9)，绕过矩形左下角，外包矩形相交但线段不相交
        assertThat(box.intersects(segment(9, 10.5, 10.5, 9))).isFalse();
    }

    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> new BoundingBox(20, 10, 10, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundingBox(10, 20, 20, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
