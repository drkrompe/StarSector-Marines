package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * The authored room layouts currently installed.
 *
 * <p>Deliberately a small mutable install point rather than a static table:
 * layouts are loaded from data at application load, replaced wholesale when a
 * catalog changes, and stood up from nothing by a test that wants to prove one
 * document does one thing. That is the same shape {@code TileRegistry} uses, and
 * for the same reason.
 *
 * <p><b>Empty is the normal state.</b> With no layout installed every room keeps
 * the procedural fitting it has always had, so this is inert until somebody
 * authors something — which is what lets it land without moving a single cell of
 * a generated deck.
 */
public final class RoomLayouts {

    private static final RoomLayouts EMPTY = new RoomLayouts(List.of());

    private static RoomLayouts installed = EMPTY;

    private final List<RoomLayout> layouts;

    public RoomLayouts(List<RoomLayout> layouts) {
        this.layouts = List.copyOf(layouts);
    }

    public static RoomLayouts installed() {
        return installed;
    }

    /** Replace the installed set. Passing an empty set restores procedural fitting everywhere. */
    public static void install(RoomLayouts replacement) {
        installed = replacement == null ? EMPTY : replacement;
    }

    /** Put back the state a fresh process starts in — for a test that installed its own. */
    public static void reset() {
        installed = EMPTY;
    }

    public List<RoomLayout> all() {
        return layouts;
    }

    public boolean isEmpty() {
        return layouts.isEmpty();
    }

    /**
     * The layout for a room of this purpose, footprint and refit level, or null
     * where none is authored and the procedural fitting should stand.
     *
     * <p>All three have to agree. Purpose alone would put a ship's armoury
     * layout into a fortress armoury of another size; the footprint is what
     * makes the binding refuse that by construction rather than by convention.
     */
    public RoomLayout find(RoomPurpose purpose, RoomShape shape, RoomFit fit) {
        for (RoomLayout layout : layouts) {
            if (layout.matches(purpose, shape, fit)) return layout;
        }
        return null;
    }

    /**
     * Every id the installed layouts name, for one check against the tile
     * catalog. A layout naming a doodad the registry lacks furnishes nothing and
     * says nothing, so it is worth failing loudly at load instead.
     */
    public List<String> doodadIds() {
        List<String> ids = new ArrayList<>();
        for (RoomLayout layout : layouts) {
            ids.addAll(layout.doodadIds());
        }
        return ids;
    }
}
