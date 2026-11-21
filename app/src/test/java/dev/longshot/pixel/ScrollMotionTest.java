package dev.longshot.pixel;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScrollMotionTest {
    @Test public void usesOnlyAReportedPositiveRangeForTheCurrentFrame() {
        ScrollMotion s = new ScrollMotion();
        s.record(0, 2, 0, 0);
        assertFalse(s.atReportedBottom());
        s.record(0, 2, 198, 200);
        assertFalse(s.atReportedBottom());
        s.record(0, 2, 200, 200);
        assertTrue(s.atReportedBottom());
        s.checkpoint();
        assertFalse(s.atReportedBottom());
    }

    @Test public void usesTwoAgreeingPositionsWhenEventsMissPartOfAScroll() {
        ScrollMotion s = new ScrollMotion();
        s.record(0, 0, 300, 0);
        s.checkpoint();
        s.record(0, 48, 400, 0);
        assertEquals(Integer.valueOf(100), s.distance(new Viewport.Distance(100), false));
        assertNull(s.distance(new Viewport.Distance(75), false));
    }

    @Test public void sumsConsumedDistanceWithConstantListOffset() {
        ScrollMotion s = new ScrollMotion();
        s.record(0, 45, 0, 0); s.record(0, 17, 0, 0);
        assertEquals(Integer.valueOf(62), s.distance(Viewport.Missing.INSTANCE, false));
        s.checkpoint(); s.record(0, 6, 0, 0);
        assertEquals(Integer.valueOf(6), s.distance(new Viewport.Distance(6), false));
    }
    @Test public void absoluteOffsetsRequireTwoObservations() {
        ScrollMotion s = new ScrollMotion();
        s.record(-1, -1, 1350, 0);
        assertNull(s.distance(Viewport.Missing.INSTANCE, false));
        s.checkpoint(); s.record(-1, -1, 1550, 0);
        assertEquals(Integer.valueOf(200), s.distance(Viewport.Missing.INSTANCE, false));
    }
    @Test public void usesChildPositionsWhenEventsLackDistance() {
        ScrollMotion s = new ScrollMotion();
        s.record(-1, -1, 0, 0);
        assertEquals(Integer.valueOf(158), s.distance(new Viewport.Distance(158), false));
    }
    @Test public void chromeFinalScrollUsesVisibleChildDistanceWhenOffsetRoundsDifferently() {
        ScrollMotion s = new ScrollMotion();
        s.record(-1, -1, 960, 0);
        s.checkpoint();
        s.record(-1, -1, 1621, 0);
        assertEquals(Integer.valueOf(660), s.distance(new Viewport.Distance(660), false));
        assertEquals(Integer.valueOf(662), s.distance(new Viewport.Distance(662), false));
        assertNull(s.distance(new Viewport.Distance(659), false));
    }
    @Test public void movingViewportRequiresScreenCoordinates() {
        ScrollMotion s = new ScrollMotion();
        s.record(0, 50, 150, 0);
        assertNull(s.distance(Viewport.Missing.INSTANCE, true));
        assertEquals(Integer.valueOf(80), s.distance(new Viewport.Distance(80), true));
        assertNull(s.distance(Viewport.Conflict.INSTANCE, false));
    }
    @Test public void declinesConflictingOrHorizontalMotion() {
        ScrollMotion s = new ScrollMotion();
        s.record(0, 45, 350, 0);
        assertNull(s.distance(new Viewport.Distance(50), false));
        s.checkpoint(); s.record(0, 45, 500, 0);
        assertNull(s.distance(Viewport.Missing.INSTANCE, false));
        s.checkpoint(); s.record(12, 20, 520, 0);
        assertNull(s.distance(new Viewport.Distance(20), false));
    }
}
