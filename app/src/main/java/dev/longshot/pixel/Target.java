package dev.longshot.pixel;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.*;

record Target(int id, Rect window, AccessibilityNodeInfo node) {
    enum VerticalScroll { NONE, END, MORE }

    static VerticalScroll verticalScroll(AccessibilityNodeInfo node) {
        if (node == null) return VerticalScroll.NONE;
        var actions = node.getActionList();
        if (actions.contains(ACTION_SCROLL_DOWN) || actions.contains(ACTION_PAGE_DOWN)) return VerticalScroll.MORE;
        if (actions.contains(ACTION_SCROLL_UP) || actions.contains(ACTION_PAGE_UP)) return VerticalScroll.END;
        if (actions.contains(ACTION_SCROLL_LEFT) || actions.contains(ACTION_SCROLL_RIGHT) ||
                actions.contains(ACTION_PAGE_LEFT) || actions.contains(ACTION_PAGE_RIGHT)) return VerticalScroll.NONE;
        if (actions.contains(ACTION_SCROLL_FORWARD)) return VerticalScroll.MORE;
        if (actions.contains(ACTION_SCROLL_BACKWARD)) return VerticalScroll.END;
        // Some gesture-driven views expose the scrollable flag without directional actions.
        return node.isScrollable() ? VerticalScroll.MORE : VerticalScroll.NONE;
    }

    static AccessibilityWindowInfo foreground(AccessibilityService service) {
        for (AccessibilityWindowInfo w : service.getWindows()) {
            if (w.isFocused()) return w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION ? w : null;
        }
        return null;
    }

    record Area(AccessibilityNodeInfo node, Rect bounds, Area parent) {
        long size() { return (long) bounds.width() * bounds.height(); }
    }
    private record Visit(AccessibilityNodeInfo node, Area parent) {}

    static Area contentArea(List<Area> areas) {
        Area largest = null;
        for (Area area : areas) {
            if (largest == null || area.size() > largest.size()) largest = area;
        }
        if (largest == null) return null;
        // A collapsing header can own the outer scroll view while the list receives the swipe.
        // Follow the central content pane, keeping small independently scrolling widgets out.
        Area selected = largest;
        for (Area area : areas) {
            if (area.parent() == selected && area.size() > largest.size() / 2 &&
                    area.bounds().contains(largest.bounds().centerX(), largest.bounds().centerY())) selected = area;
        }
        return selected;
    }

    static Target select(AccessibilityWindowInfo w) {
        Rect window = new Rect();
        w.getBoundsInScreen(window);
        List<Area> areas = new ArrayList<>();
        ArrayDeque<Visit> queue = new ArrayDeque<>();
        AccessibilityNodeInfo root = w.getRoot();
        if (root != null) queue.add(new Visit(root, null));
        for (int seen = 0; !queue.isEmpty() && seen < 600; seen++) {
            Visit visit = queue.remove();
            AccessibilityNodeInfo n = visit.node();
            Area parent = visit.parent();
            if (n.isVisibleToUser() && verticalScroll(n) != VerticalScroll.NONE) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (r.intersect(window) && r.height() >= window.height() / 4) {
                    parent = new Area(n, r, parent);
                    areas.add(parent);
                }
            }
            for (int i = 0; i < n.getChildCount() && seen + queue.size() < 600; i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(new Visit(child, parent));
            }
        }
        Area selected = contentArea(areas);
        return new Target(w.getId(), window, selected == null ? null : selected.node());
    }
}
