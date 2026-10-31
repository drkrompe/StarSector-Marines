package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitOrder;
import com.dillon.starsectormarines.campaign.refit.RefitText;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The Second Wind's refit tree as the Engineering view draws it: the five cores side by side, and
 * every other system hanging down its core's spine as a row of its own. What a system's next tier
 * also needs from other cores is marked beside it rather than drawn across the tree.
 */
final class ShipSystemsTree {
    /** Which core each system hangs from, in the order its column lays them out. */
    static final Map<RefitTrack, List<RefitTrack>> BRANCHES = new EnumMap<>(Map.of(
            RefitTrack.POWER, List.of(RefitTrack.MECH_COOLANT),
            RefitTrack.LIFE_SUPPORT, List.of(RefitTrack.MESS, RefitTrack.GYM, RefitTrack.SICK_BAY, RefitTrack.REC_LOUNGE),
            RefitTrack.STRUCTURE, List.of(RefitTrack.RANGE, RefitTrack.RESERVE_HANGAR),
            RefitTrack.COMPUTE, List.of(RefitTrack.MECH_CALIBRATION),
            RefitTrack.FABRICATION, List.of(RefitTrack.MECH_ARMOR, RefitTrack.MECH_REPAIR)));

    static final String LINE = "#738578", LINE_ON = "#dec58a";
    private static final RefitBand[] TIERS = {RefitBand.RESTORED, RefitBand.IMPROVED_I, RefitBand.IMPROVED_II};

    enum Node { OWNED, AVAILABLE, LOCKED, ABSENT }

    private ShipSystemsTree() {}

    /** A tier is owned once bought, available when nothing but price stands in its way, locked otherwise. */
    static Node node(ShipCondition state, RefitTrack track, RefitBand tier) {
        if (tier.ordinal() > RefitOrder.ceiling(track).ordinal()) return Node.ABSENT;
        if (state.band(track).atLeast(tier)) return Node.OWNED;
        RefitOrder order = state.next(track);
        if (track == RefitTrack.FABRICATION || order == null || order.band != tier) return Node.LOCKED;
        return state.missing(order) == null ? Node.AVAILABLE : Node.LOCKED;
    }

    static List<Map<String, Object>> cores(ShipCondition state, RefitTrack selected, Function<RefitTrack, Runnable> select) {
        var cores = new ArrayList<Map<String, Object>>();
        var roots = List.copyOf(BRANCHES.keySet());
        for (int i = 0; i < roots.size(); i++) {
            RefitTrack core = roots.get(i);
            var subs = BRANCHES.get(core);
            boolean restored = state.band(core).atLeast(RefitBand.RESTORED);
            var m = row(state, core, selected, select, "systems-core");
            String id = (String) m.get("id");
            m.put("barId", id + "-bar"); m.put("barLeftId", id + "-bar-left"); m.put("barRightId", id + "-bar-right");
            m.put("barLeftStyle", "background-color: " + (i == 0 ? "transparent" : LINE) + ";");
            m.put("barRightStyle", "background-color: " + (i == roots.size() - 1 ? "transparent" : LINE) + ";");
            m.put("dropRowId", id + "-drop-row"); m.put("dropPadId", id + "-drop-pad"); m.put("dropId", id + "-drop");
            m.put("dropStyle", vertical(restored, DROP));
            m.put("stateId", id + "-state"); m.put("textId", id + "-text");
            m.put("state", RefitText.tier(core, state.band(core)));
            m.put("stateClasses", "label systems-core-state " + bandClass(state.band(core)));
            m.put("subsId", id + "-subs"); m.put("spinePadId", id + "-spine-pad");
            m.put("spineId", id + "-spine"); m.put("rowsId", id + "-rows");
            // The spine runs from the core's row down to the middle of its last system.
            m.put("spineStyle", vertical(restored, subs.isEmpty() ? 0 : ROWS_TOP + (subs.size() - 1) * ROW_PITCH + ROW_HEIGHT / 2));
            var rows = new ArrayList<Map<String, Object>>();
            for (RefitTrack sub : subs) {
                var r = row(state, sub, selected, select, "systems-row");
                String rid = (String) r.get("id");
                r.put("wrapId", rid + "-wrap"); r.put("elbowId", rid + "-elbow");
                r.put("elbowStyle", "background-color: " + (restored ? LINE_ON : LINE) + "; height: " + (restored ? 3 : 2) + "px;");
                rows.add(Map.copyOf(r));
            }
            m.put("subs", List.copyOf(rows));
            cores.add(Map.copyOf(m));
        }
        return List.copyOf(cores);
    }

    /** Pixel geometry the spine is drawn to; the markup's row classes use the same numbers. */
    static final int ROWS_TOP = 8, ROW_HEIGHT = 64, ROW_PITCH = 72, DROP = 10;

    /**
     * One system as one row button: icon, name, its tiers left to right, and only what the next
     * tier waits on. The whole row selects the system, so the nodes are pictures, not controls.
     */
    private static Map<String, Object> row(ShipCondition state, RefitTrack track, RefitTrack selected,
                                           Function<RefitTrack, Runnable> select, String size) {
        String id = "systems-" + slug(track);
        var m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("headerId", id + "-header"); m.put("iconId", id + "-icon"); m.put("nameId", id + "-name");
        m.put("headerStyle", selectedStyle(track == selected));
        m.put("icon", icon(track));
        m.put("label", RefitText.track(track));
        m.put("select", select.apply(track));
        m.put("nodesId", id + "-nodes");
        m.put("topId", id + "-top"); m.put("bottomId", id + "-bottom");
        for (int k = 1; k <= 3; k++) {
            RefitBand tier = TIERS[k - 1];
            Node node = node(state, track, tier);
            boolean absent = node == Node.ABSENT;
            m.put("node" + k + "Id", id + "-node-" + k);
            m.put("node" + k + "Classes", absent ? "hidden" : size + "-node");
            m.put("node" + k + "Icon", absent ? "" : "graphics/ui/refit/node-" + k + "-" + node.name().toLowerCase(Locale.ROOT) + ".svg");
            if (k > 1) {
                m.put("link" + k + "Id", id + "-link-" + k);
                m.put("link" + k + "Classes", absent ? "hidden" : size + "-link");
                m.put("link" + k + "Style", "background-color: " + (node == Node.OWNED ? LINE_ON : LINE) + "; height: "
                        + (node == Node.OWNED ? 3 : 2) + "px;");
            }
        }
        RefitOrder next = state.next(track);
        var needs = next == null || track == RefitTrack.FABRICATION ? List.<Map.Entry<RefitTrack, RefitBand>>of()
                : List.copyOf(next.requires.entrySet());
        m.put("needsId", id + "-needs");
        for (int j = 1; j <= 3; j++) {
            String need = "need" + j;
            m.put(need + "Id", id + "-need-" + j);
            m.put(need + "IconId", id + "-need-" + j + "-icon");
            if (j <= needs.size()) {
                var entry = needs.get(j - 1);
                boolean met = state.band(entry.getKey()).atLeast(entry.getValue());
                m.put(need + "Classes", size + "-need");
                m.put(need + "Style", "border-color: " + (met ? LINE_ON : "#d88a6c") + ";");
                m.put(need + "Icon", icon(entry.getKey()));
            } else {
                m.put(need + "Classes", "hidden");
                m.put(need + "Style", "");
                m.put(need + "Icon", "");
            }
        }
        return m;
    }

    private static String selectedStyle(boolean selected) {
        // Inline, because the shared button theme outranks a class for a button's ground.
        return selected
                ? "background-color: #3a3524; background-image: none; border-width: 1px; border-color: #e6d28c; transition: none;"
                : "background-color: #1a2420; background-image: none; border-width: 1px; border-color: #34423b; transition: none;";
    }

    private static String vertical(boolean on, int height) {
        return "background-color: " + (on ? LINE_ON : LINE) + "; width: " + (on ? 3 : 2) + "px; height: " + height + "px;";
    }

    static String icon(RefitTrack track) { return "graphics/ui/refit/track-" + slug(track) + ".svg"; }

    static String bandClass(RefitBand band) {
        return band == RefitBand.DEFICIT ? "refit-state-deficit" : band == RefitBand.RESTORED ? "refit-state-restored" : "refit-state-improved";
    }

    static String bandColor(RefitBand band) {
        return band == RefitBand.DEFICIT ? "#d88a6c" : band == RefitBand.RESTORED ? LINE_ON : "#8fd0b8";
    }

    static String slug(RefitTrack track) { return track.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
}
