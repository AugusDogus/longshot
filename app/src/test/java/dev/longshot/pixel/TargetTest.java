package dev.longshot.pixel;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TargetTest {
    private static AccessibilityNodeInfo scrollable(AccessibilityNodeInfo.AccessibilityAction... actions) {
        var node = new AccessibilityNodeInfo();
        node.setScrollable(true);
        for (var action : actions) node.addAction(action);
        return node;
    }

    private static boolean canContinue(AccessibilityNodeInfo node) {
        return Target.verticalScroll(node) == Target.VerticalScroll.MORE;
    }

    @Test public void moreFollowsVerticalDirectionAtTheTopMiddleAndBottom() {
        assertTrue(canContinue(scrollable(ACTION_SCROLL_DOWN)));
        assertTrue(canContinue(scrollable(ACTION_SCROLL_UP, ACTION_SCROLL_DOWN)));
        assertFalse(canContinue(scrollable(ACTION_SCROLL_UP, ACTION_SCROLL_RIGHT, ACTION_SCROLL_FORWARD)));
        assertFalse(canContinue(scrollable(ACTION_SCROLL_BACKWARD)));
    }

    @Test public void handlesHorizontalStaticAndGestureOnlyViews() {
        assertFalse(canContinue(scrollable(ACTION_SCROLL_RIGHT, ACTION_SCROLL_FORWARD)));
        assertFalse(canContinue(null));
        assertFalse(canContinue(new AccessibilityNodeInfo()));
        assertTrue(canContinue(scrollable()));
        assertTrue(canContinue(scrollable(ACTION_SCROLL_FORWARD)));
    }

    @Test public void selectsTheMainNestedListUnderACollapsingHeader() {
        var wrapper = new Target.Area(scrollable(ACTION_SCROLL_BACKWARD), new Rect(0, 150, 1344, 2920), null);
        var list = new Target.Area(scrollable(ACTION_SCROLL_FORWARD), new Rect(0, 688, 1344, 2920), wrapper);
        var widget = new Target.Area(scrollable(), new Rect(100, 900, 500, 1700), list);
        assertSame(list, Target.contentArea(List.of(wrapper, list, widget)));
        var smallPane = new Target.Area(scrollable(), widget.bounds(), wrapper);
        assertSame(wrapper, Target.contentArea(List.of(wrapper, smallPane)));
    }

    @Test public void recognizesExplicitVerticalActionsEvenWithoutScrollableFlag() {
        var node = new AccessibilityNodeInfo();
        node.addAction(ACTION_PAGE_DOWN);
        assertTrue(canContinue(node));
    }
}
