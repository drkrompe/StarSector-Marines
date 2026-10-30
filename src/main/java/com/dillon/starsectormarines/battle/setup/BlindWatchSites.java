package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.setup.BlindWatchSequence.Sites;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.Installation;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.Layout;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.Pillbox;
import com.dillon.starsectormarines.battle.world.gen.BlindWatchSounding.PostRole;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads Blind Watch's {@link Sites} off the Sounding's authored {@link Layout}, and says which of its posts the watch
 * mans; the map owns where, the sequence what.
 */
public final class BlindWatchSites {
    /**
     * Hands on watch at a quiet post: half its 53 posts, the rest asleep in the bunk modules. A stood-to post doubles it
     * ({@code BlindWatchProgress.sentries}) and so mans every post. UNTUNED first pass.
     */
    public static final int USUAL_WATCH = 27;

    private BlindWatchSites() {}

    /** The mission's sites, with the mortar carrier's track as the setup planned it for its hull. */
    public static Sites from(Layout layout, List<Cell> mortarTrack) {
        Set<Cell> hut = new HashSet<>(area(layout.controlHut()).cells());
        hut.add(cell(layout.console()));
        List<Cell> bunkers = new ArrayList<>();
        for (Pillbox pillbox : layout.pillboxes())
            bunkers.add(new Cell(pillbox.building().interiorAnchorX, pillbox.building().interiorAnchorY));
        return new Sites(area(layout.landing()), plant(layout, Installation.MAST), plant(layout, Installation.UPLINK),
                plant(layout, Installation.DISH_FIELD), area(layout.riggersModule()), cells(layout.riggers()),
                new Area(hut), bunkers, mortarTrack, area(layout.podRacks()));
    }

    /**
     * The posts the watch mans, {@code count} of them: every pillbox slit first, so the pillboxes are always crewed,
     * then the watch boss, then one post of each duty in turn. Each duty keeps its posts' order. Pure.
     */
    public static Map<PostRole, List<BlindWatchSounding.Cell>> watch(Map<PostRole, List<BlindWatchSounding.Cell>> posts,
                                                                     Set<BlindWatchSounding.Cell> slits, int count) {
        Set<BlindWatchSounding.Cell> manned = new LinkedHashSet<>();
        for (List<BlindWatchSounding.Cell> cells : posts.values())
            for (BlindWatchSounding.Cell cell : cells) if (slits.contains(cell) && manned.size() < count) manned.add(cell);
        for (BlindWatchSounding.Cell cell : posts.getOrDefault(PostRole.WATCH_BOSS, List.of()))
            if (manned.size() < count) manned.add(cell);
        for (int round = 0, rounds = longest(posts); round < rounds && manned.size() < count; round++)
            for (List<BlindWatchSounding.Cell> cells : posts.values()) {
                List<BlindWatchSounding.Cell> open = cells.stream().filter(cell -> !slits.contains(cell)).toList();
                if (round < open.size() && manned.size() < count) manned.add(open.get(round));
            }
        Map<PostRole, List<BlindWatchSounding.Cell>> out = new EnumMap<>(PostRole.class);
        for (Map.Entry<PostRole, List<BlindWatchSounding.Cell>> duty : posts.entrySet()) {
            List<BlindWatchSounding.Cell> cells = duty.getValue().stream().filter(manned::contains).toList();
            if (!cells.isEmpty()) out.put(duty.getKey(), cells);
        }
        return out;
    }

    private static int longest(Map<PostRole, List<BlindWatchSounding.Cell>> posts) {
        int longest = 0;
        for (List<BlindWatchSounding.Cell> cells : posts.values()) longest = Math.max(longest, cells.size());
        return longest;
    }

    private static Cell plant(Layout layout, Installation installation) {
        return cell(layout.sites().get(installation).plant());
    }

    static Cell cell(BlindWatchSounding.Cell cell) { return new Cell(cell.x(), cell.y()); }

    static List<Cell> cells(List<BlindWatchSounding.Cell> cells) {
        List<Cell> out = new ArrayList<>(cells.size());
        for (BlindWatchSounding.Cell cell : cells) out.add(cell(cell));
        return out;
    }

    static Area area(BlindWatchSounding.Area area) {
        Set<Cell> out = new HashSet<>();
        for (BlindWatchSounding.Cell cell : area.cells()) out.add(cell(cell));
        return new Area(out);
    }

    /** A building's whole footprint, walls included. */
    static Area area(PointOfInterest building) { return DustLedgerSites.area(building); }
}
