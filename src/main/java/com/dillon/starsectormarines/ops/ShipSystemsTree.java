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
 * The Second Wind's refit tree as the Engineering view draws it: the hull at the root, the five cores
 * hanging from it, and every other system hanging from the core it leans on most. The cross-links a
 * tier also needs are listed on the detail card rather than drawn, so the tree stays a tree.
 */
final class ShipSystemsTree {
    /** Which core each system hangs from, in the order its column lays them out. */
    static final Map<RefitTrack, List<RefitTrack>> BRANCHES = new EnumMap<>(Map.of(
            RefitTrack.POWER, List.of(RefitTrack.DRIVE, RefitTrack.FLUX),
            RefitTrack.LIFE_SUPPORT, List.of(RefitTrack.MESS, RefitTrack.GYM, RefitTrack.SICK_BAY, RefitTrack.REC_LOUNGE,
                    RefitTrack.HYDROPONICS),
            RefitTrack.STRUCTURE, List.of(RefitTrack.HULL, RefitTrack.BRIG, RefitTrack.RANGE, RefitTrack.HOLDS),
            RefitTrack.COMPUTE, List.of(RefitTrack.SENSORS, RefitTrack.WARDROOM, RefitTrack.BRIEFING),
            RefitTrack.FABRICATION, List.of()));

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
            var m = column(state, core, selected, select, i == 0, i == roots.size() - 1, LINE, true, !subs.isEmpty());
            String id = (String) m.get("id");
            boolean restored = state.band(core).atLeast(RefitBand.RESTORED);
            // Weighted by what hangs below, so Life support's four rooms get the width they need.
            m.put("columnStyle", "flex-grow: " + Math.max(3, 2 * subs.size()) + ";");
            m.put("subDropId", id + "-sub-drop");
            m.put("subDropClasses", subs.isEmpty() ? "hidden" : "systems-sub-drop");
            m.put("subDropStyle", line(restored));
            m.put("subsId", id + "-subs");
            var rows = new ArrayList<Map<String, Object>>();
            for (int j = 0; j < subs.size(); j++)
                rows.add(Map.copyOf(column(state, subs.get(j), selected, select, j == 0, j == subs.size() - 1,
                        restored ? LINE_ON : LINE, false, false)));
            m.put("subs", List.copyOf(rows));
            cores.add(Map.copyOf(m));
        }
        return List.copyOf(cores);
    }

    /** One column: the bar it hangs from, its header, then a slot per tier joined by links. */
    private static Map<String, Object> column(ShipCondition state, RefitTrack track, RefitTrack selected,
                                              Function<RefitTrack, Runnable> select, boolean first, boolean last,
                                              String bar, boolean core, boolean stems) {
        String id = "systems-" + slug(track);
        RefitBand band = state.band(track);
        var m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("barId", id + "-bar"); m.put("barLeftId", id + "-bar-left"); m.put("barRightId", id + "-bar-right");
        m.put("barLeftStyle", "background-color: " + (first ? "transparent" : bar) + ";");
        m.put("barRightStyle", "background-color: " + (last ? "transparent" : bar) + ";");
        m.put("dropId", id + "-drop");
        m.put("dropStyle", line(band.atLeast(RefitBand.RESTORED)));
        m.put("headerId", id + "-header"); m.put("iconId", id + "-icon"); m.put("nameId", id + "-name"); m.put("stateId", id + "-state");
        m.put("headerClasses", core ? "systems-header systems-core-header" : "systems-header systems-sub-header");
        // Inline, because the shared button theme outranks a class for a button's ground.
        m.put("headerStyle", track == selected
                ? "background-color: #3a3524; background-image: none; border-width: 1px; border-color: #e6d28c; transition: none;"
                : "background-color: #1a2420; background-image: none; border-width: 1px; border-color: #34423b; transition: none;");
        m.put("icon", icon(track));
        m.put("label", RefitText.track(track));
        m.put("state", RefitText.tier(track, band));
        m.put("stateClasses", core ? "label systems-state " + bandClass(band) : "hidden");
        Runnable pick = select.apply(track);
        m.put("select", pick);
        String size = core ? "systems-core" : "systems-sub";
        for (int k = 1; k <= 3; k++) {
            RefitBand tier = TIERS[k - 1];
            Node node = node(state, track, tier);
            boolean absent = node == Node.ABSENT;
            m.put("slot" + k + "Id", id + "-slot-" + k);
            m.put("slot" + k + "Classes", absent && !stems ? "hidden" : size + "-slot");
            m.put("node" + k + "Id", id + "-node-" + k);
            m.put("node" + k + "ImageId", id + "-node-" + k + "-image");
            m.put("node" + k + "Classes", absent ? "hidden" : size + "-node");
            m.put("node" + k + "Icon", absent ? "" : "graphics/ui/refit/node-" + k + "-" + node.name().toLowerCase(Locale.ROOT) + ".svg");
            m.put("node" + k + "Select", pick);
            m.put("spacer" + k + "Id", id + "-spacer-" + k);
            m.put("center" + k + "Id", id + "-center-" + k);
            m.put("needs" + k + "Id", id + "-needs-" + k);
            // What this tier waits on, read at a glance: gold once met, red until then.
            var order = absent || node == Node.OWNED ? null : RefitOrder.after(track, RefitBand.values()[tier.ordinal() - 1]);
            var needs = order == null ? List.<Map.Entry<RefitTrack, RefitBand>>of() : List.copyOf(order.requires.entrySet());
            for (int j = 1; j <= 3; j++) {
                String need = "need" + k + j;
                m.put(need + "Id", id + "-need-" + k + "-" + j);
                m.put(need + "IconId", id + "-need-" + k + "-" + j + "-icon");
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
            m.put("stem" + k + "Id", id + "-stem-" + k);
            m.put("stem" + k + "Classes", absent && stems ? size + "-stem" : "hidden");
            m.put("stem" + k + "Style", line(band.atLeast(RefitBand.RESTORED)));
            if (k > 1) {
                boolean shown = !absent || stems;
                m.put("link" + k + "Id", id + "-link-" + k);
                m.put("link" + k + "Classes", shown ? size + "-link" : "hidden");
                m.put("link" + k + "Style", line(absent ? band.atLeast(RefitBand.RESTORED) : node == Node.OWNED));
            }
        }
        return m;
    }

    static String icon(RefitTrack track) { return "graphics/ui/refit/track-" + slug(track) + ".svg"; }

    static String bandClass(RefitBand band) {
        return band == RefitBand.DEFICIT ? "refit-state-deficit" : band == RefitBand.RESTORED ? "refit-state-restored" : "refit-state-improved";
    }

    static String bandColor(RefitBand band) {
        return band == RefitBand.DEFICIT ? "#d88a6c" : band == RefitBand.RESTORED ? LINE_ON : "#8fd0b8";
    }

    private static String line(boolean on) {
        return "background-color: " + (on ? LINE_ON : LINE) + "; width: " + (on ? 3 : 2) + "px;";
    }

    static String slug(RefitTrack track) { return track.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
}
