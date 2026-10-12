package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;

import java.util.ArrayList;
import java.util.List;

/**
 * What the hover-only help tab says and when it is open. Pure: no markup, no game state.
 * The bindings listed here mirror {@code BattleDirectControlInput} and the carrier code;
 * the descriptions live in the strings file under {@code battleDirectHelp*}.
 */
final class BattleDirectControlHelp {
    static final float TAB_WIDTH = 64f;
    static final float TAB_HEIGHT = 20f;
    static final float PANEL_WIDTH = 340f;
    static final float TITLE_HEIGHT = 20f;
    static final float ROW_HEIGHT = 14f;
    static final float PANEL_PADDING = 6f;
    /** Gap between the help tab and the exit/pause strip, in document units. */
    static final float TAB_GAP = 6f;
    /** The longest description a row can show without clipping at {@link #PANEL_WIDTH}. */
    static final int MAX_TEXT_CHARS = 46;

    private BattleDirectControlHelp() { }

    /** One row: the control (a hardware key name, not prose) and the strings key of what it does. */
    record Line(String keys, String textKey) { }

    /** The panel heading's strings key for a carrier, or empty when nothing is controlled. */
    static String titleKey(Carrier carrier) {
        return carrier == null || carrier == Carrier.NONE ? "" : "battleDirectHelpTitle" + carrier.name();
    }

    /** The rows for a carrier. Nothing controlled, nothing to explain. */
    static List<Line> lines(Carrier carrier) {
        if (carrier == null) return List.of();
        List<Line> out = new ArrayList<>();
        switch (carrier) {
            case MARINE -> {
                add(out, "WASD", "MarineMove");
                add(out, "Mouse", "MarineAim");
                add(out, "LMB hold", "MarineFire");
                add(out, "RMB", "MarineSecondary");
                add(out, "E", "MarineShield");
                add(out, "G", "MarineSmoke");
                add(out, "Shift", "MarineSprint");
                add(out, "Tab / Esc", "Leave");
            }
            case MECH -> {
                add(out, "WASD", "MechMove");
                add(out, "Mouse", "MechAim");
                add(out, "LMB hold", "MechPrimary");
                add(out, "RMB hold", "MechSecondary");
                add(out, "1", "MechDefault");
                add(out, "2 3 4", "MechPick");
                add(out, "", "MechHeat");
                add(out, "", "MechIndirect");
                add(out, "Tab / Esc", "Leave");
            }
            case VEHICLE -> {
                add(out, "W / S", "VehicleDrive");
                add(out, "A / D", "VehicleSteer");
                add(out, "Mouse", "VehicleAim");
                add(out, "LMB hold", "VehicleFire");
                add(out, "Tab / Esc", "Leave");
            }
            case WARSHIP -> {
                add(out, "WASD", "ShipFly");
                add(out, "LMB hold", "ShipGuns");
                add(out, "RMB hold", "ShipMissiles");
                add(out, "1", "ShipHold");
                add(out, "2", "ShipDirector");
                add(out, "3", "ShipPreset");
                add(out, "4", "ShipPolicy");
                add(out, "E", "ShipShield");
                add(out, "Tab / Esc", "Leave");
                add(out, "", "ShipLaid");
                add(out, "", "ShipSeen");
                add(out, "", "ShipDanger");
                add(out, "", "ShipFlux");
                add(out, "", "ShipLances");
                add(out, "", "ShipPd");
            }
            case NONE -> { }
        }
        return List.copyOf(out);
    }

    private static void add(List<Line> out, String keys, String suffix) {
        out.add(new Line(keys, "battleDirectHelp" + suffix));
    }

    /** Document height of the expanded panel for a row count. */
    static float panelHeight(int rows) {
        return TITLE_HEIGHT + rows * ROW_HEIGHT + PANEL_PADDING * 2f;
    }

    /** A screen rectangle, origin bottom-left like the host's pointer. Right and top edges are exclusive. */
    record Rect(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** The tab and the panel it opens, in screen space. The panel hangs directly under the tab. */
    record Layout(Rect tab, Rect panel) {
        Rect union() {
            float left = Math.min(tab.x(), panel.x());
            float bottom = Math.min(tab.y(), panel.y());
            float right = Math.max(tab.x() + tab.width(), panel.x() + panel.width());
            float top = Math.max(tab.y() + tab.height(), panel.y() + panel.height());
            return new Rect(left, bottom, right - left, top - bottom);
        }
    }

    /**
     * Places the tab immediately left of the exit/pause strip, top-aligned with it, and the
     * panel under the tab with their right edges flush.
     *
     * @param actionsLeft screen x of the strip's left edge
     * @param top screen y of the strip's top edge
     */
    static Layout layout(float actionsLeft, float top, float scale, int rows) {
        float tabWidth = TAB_WIDTH * scale;
        float tabHeight = TAB_HEIGHT * scale;
        float tabX = actionsLeft - TAB_GAP * scale - tabWidth;
        Rect tab = new Rect(tabX, top - tabHeight, tabWidth, tabHeight);
        float panelWidth = PANEL_WIDTH * scale;
        float panelHeight = panelHeight(rows) * scale;
        Rect panel = new Rect(tabX + tabWidth - panelWidth, tab.y() - panelHeight, panelWidth, panelHeight);
        return new Layout(tab, panel);
    }

    /**
     * Hover-only open state. The panel opens when the pointer is over the tab, stays open while
     * the pointer is over the tab or the panel, and closes the moment it leaves both, or when
     * nothing is controlled. It holds no click or focus.
     */
    static final class Hover {
        private boolean expanded;

        boolean expanded() { return expanded; }

        boolean update(boolean controlled, boolean pointerKnown, float x, float y, Layout layout) {
            if (!controlled || !pointerKnown) {
                expanded = false;
            } else if (layout.tab().contains(x, y)) {
                expanded = true;
            } else if (expanded && !layout.panel().contains(x, y)) {
                expanded = false;
            }
            return expanded;
        }

        /** Whether the pointer is over visible help chrome. A collapsed panel captures nothing. */
        boolean blocks(boolean controlled, float x, float y, Layout layout) {
            return controlled && (layout.tab().contains(x, y)
                    || expanded && layout.panel().contains(x, y));
        }

        void collapse() { expanded = false; }
    }
}
