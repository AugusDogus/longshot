package dev.longshot.pixel;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ViewportTest {
    private final Viewport.Anchor first = anchor("First"), second = anchor("Second"),
            third = anchor("Third"), fourth = anchor("Fourth");

    private Viewport.Anchor anchor(String label) {
        return new Viewport.Anchor(null, label, label, 0, 140, 68);
    }
    private Viewport at(Map<Viewport.Anchor, Integer> tops) { return new Viewport(null, tops); }

    @Test public void twoMovingRowsAgreeWhilePinnedItemStaysStill() {
        var before = at(Map.of(first, 30, second, 280, third, 490));
        var after = at(Map.of(first, 30, second, 150, third, 360));
        assertEquals(new Viewport.Distance(130), before.childDistance(after));
    }
    @Test public void repeatedObservationsAtOnePositionAreInsufficient() {
        var before = at(Map.of(first, 280, second, 280));
        var after = at(Map.of(first, 150, second, 150));
        assertEquals(Viewport.Missing.INSTANCE, before.childDistance(after));
    }
    @Test public void incompatibleGroupsHaveNoSingleDisplacement() {
        var before = at(Map.of(first, 100, second, 200, third, 450, fourth, 670));
        var after = at(Map.of(first, 80, second, 180, third, 340, fourth, 560));
        assertEquals(Viewport.Conflict.INSTANCE, before.childDistance(after));
    }
}
