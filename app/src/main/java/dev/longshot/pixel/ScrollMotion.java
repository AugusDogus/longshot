package dev.longshot.pixel;

/** Scroll events from the selected node between two captures. Android uses -1 for an absent delta. */
final class ScrollMotion {
    private long sum;
    private boolean received, complete = true, horizontal;
    private Integer startOffset, endOffset;
    private boolean atReportedBottom;
    int revision;

    void record(int dx, int dy, int y, int maxY) {
        revision++;
        received = true;
        if (dx != 0 && dx != -1) horizontal = true;
        if (dy == -1) complete = false;
        else sum += dy;
        endOffset = y >= 0 ? Integer.valueOf(y) : null;
        // Both fields default to zero when a view omits its range.
        atReportedBottom = maxY > 0 && y == maxY;
    }

    boolean atReportedBottom() { return atReportedBottom; }

    Integer distance(Viewport.Evidence geometry, boolean movedOrigin) {
        if (horizontal || geometry == Viewport.Conflict.INSTANCE) return null;
        Integer children = geometry instanceof Viewport.Distance d ? Integer.valueOf(d.pixels()) : null;
        // When the viewport itself moves, content offsets and shifts on screen use different origins.
        if (movedOrigin) return children;
        // RecyclerView can expose a constant scrollY while reporting consumed pixel deltas.
        Long measured = received && complete ? Long.valueOf(sum) : null;
        if (startOffset != null && endOffset != null && !startOffset.equals(endOffset)) {
            long absolute = (long) endOffset - startOffset;
            if (measured != null && measured != absolute &&
                    (children == null || Math.abs(children.longValue() - absolute) > 1)) return null;
            measured = absolute;
        }
        if (measured == null) return children;
        if (measured < 0 || measured > Integer.MAX_VALUE) return null;
        if (children != null) {
            // Chrome can round its scroll offset and on-screen child positions one pixel apart.
            return Math.abs(children.longValue() - measured) <= 1 ? children : null;
        }
        return measured.intValue();
    }

    void checkpoint() {
        if (received) startOffset = endOffset;
        sum = 0;
        received = false;
        atReportedBottom = false;
        complete = true;
        horizontal = false;
    }
}
