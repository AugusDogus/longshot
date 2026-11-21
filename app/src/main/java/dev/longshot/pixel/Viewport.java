package dev.longshot.pixel;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Visible child positions in screen coordinates, sampled alongside a window screenshot. */
record Viewport(Rect bounds, Map<Anchor, Integer> tops) {
    record Anchor(AccessibilityNodeInfo node, String label, String uniqueId, int left, int width, int height) {}

    sealed interface Evidence permits Distance, Missing, Conflict {}
    record Distance(int pixels) implements Evidence {}
    enum Missing implements Evidence { INSTANCE }
    enum Conflict implements Evidence { INSTANCE }

    static Viewport read(Target target) {
        AccessibilityNodeInfo root = target.node();
        if (root == null || !root.refresh()) return null;
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        if (!bounds.intersect(target.window())) return null;
        Map<Anchor, Integer> tops = new HashMap<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        for (int visited = 0; !queue.isEmpty() && visited < 200; visited++) {
            AccessibilityNodeInfo n = queue.remove();
            if (!n.isVisibleToUser() || (!n.equals(root) && n.isScrollable())) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (!n.isPassword() && r.top > bounds.top && r.bottom < bounds.bottom && !r.isEmpty()) {
                CharSequence text = n.getText() == null ? n.getContentDescription() : n.getText();
                String id = n.getUniqueId();
                // A recycled list row can keep its node ID while displaying a different item.
                if ((text != null && text.length() > 0) || id != null)
                    tops.put(new Anchor(n, text == null ? "" : text.toString(), id, r.left, r.width(), r.height()), r.top);
            }
            for (int i = 0; i < n.getChildCount() && queue.size() + visited < 200; i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return new Viewport(bounds, tops);
    }

    /** Two distinct child positions must agree. Pinned children contribute no movement. */
    Evidence childDistance(Viewport next) {
        Map<Integer, Set<Integer>> positions = new HashMap<>();
        for (var child : tops.entrySet()) {
            Integer newTop = next.tops.get(child.getKey());
            if (newTop == null) continue;
            int shift = child.getValue() - newTop;
            if (shift > 0) positions.computeIfAbsent(shift, unused -> new HashSet<>()).add(newTop);
        }
        Integer distance = null;
        for (var candidate : positions.entrySet()) {
            if (candidate.getValue().size() < 2) continue;
            if (distance != null) return Conflict.INSTANCE;
            distance = candidate.getKey();
        }
        return distance == null ? Missing.INSTANCE : new Distance(distance);
    }
}
