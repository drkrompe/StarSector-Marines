package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A mission's own map ({@code owned-maps.md}): one layout, authored once and read from
 * {@code data/world/maps/<id>.map.json}, which a {@link BiomeSkin} dresses for whatever world the mission is fought on.
 * Everything here is form and purpose (where the ground is worked, where a drop runs, which building is the mission's
 * hall); nothing names a material, a drop style or a building's walls, which are the skin's. {@link OwnedMapRecipe}
 * builds it, {@link OwnedMapJson} reads and writes it, {@link OwnedMapCatalog} holds the installed ones.
 *
 * <p>Cells are map cells, x east and y as the grid's rows (the same frame as the road and terrain documents the map
 * names). A {@link Box} is inclusive on both corners.
 *
 * @param id          the map's id, the document's file stem
 * @param width       map width in cells; the road and terrain documents must have the same extent
 * @param height      map height in cells
 * @param roads       the road document the map lays ({@code data/world/roads/...}), or null for none
 * @param roadGraphFrom the road the published road graph starts from, or null for none
 * @param terrain     the terrain document (inaccessible masses) the map lays, or null for none
 * @param origin      where the attacker arrives: every route is proved from here
 * @param objective   the landscape's second anchor, toward the objective
 * @param grounds     worked ground laid by role: yards, markings, gravel beds
 * @param pads        landing pads
 * @param sites       the mission's named sites, by name
 * @param settlements the settlements and works, by program and role
 * @param walls       straight walls laid by hand (a screen, a revetment)
 * @param props       doodads set down by hand
 * @param drops       natural drops, by role
 * @param rises       natural rises (a gorge floor's banks), by role
 * @param routes      cells every skin must still reach from {@link #origin}, beyond the pads, sites and buildings
 */
public record OwnedMap(String id, int width, int height, String roads, String roadGraphFrom, String terrain,
                       Cell origin, Cell objective, List<Ground> grounds, List<Pad> pads, List<Site> sites,
                       List<Settlement> settlements, List<Wall> walls, List<Prop> props, List<Drop> drops,
                       List<Rise> rises, List<Cell> routes) {

    /** The document version this reader writes; a newer one is refused. */
    public static final int VERSION = 1;

    public OwnedMap {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("an owned map needs an id");
        if (width < 16 || height < 16) throw new IllegalArgumentException(id + ": map is too small");
        if (origin == null || objective == null) throw new IllegalArgumentException(id + ": origin and objective");
        grounds = List.copyOf(grounds);
        pads = List.copyOf(pads);
        sites = List.copyOf(sites);
        settlements = List.copyOf(settlements);
        walls = List.copyOf(walls);
        props = List.copyOf(props);
        drops = List.copyOf(drops);
        rises = List.copyOf(rises);
        routes = List.copyOf(routes);
        Set<String> names = new HashSet<>();
        for (Site s : sites) if (!names.add("site:" + s.name())) throw new IllegalArgumentException(id + ": site named twice: " + s.name());
        Set<String> buildings = new HashSet<>();
        for (Settlement s : settlements) {
            if (!names.add("settlement:" + s.name())) throw new IllegalArgumentException(id + ": settlement named twice: " + s.name());
            for (Building b : s.buildings()) if (!buildings.add(b.name())) throw new IllegalArgumentException(id + ": building named twice: " + b.name());
        }
        for (Site s : sites) {
            if (s.building() != null && !buildings.contains(s.building()))
                throw new IllegalArgumentException(id + ": site " + s.name() + " names no building " + s.building());
        }
        Set<String> drops2 = new HashSet<>();
        for (Drop d : drops) if (!drops2.add(d.name())) throw new IllegalArgumentException(id + ": drop named twice: " + d.name());
        for (Settlement s : settlements) {
            if (s.terraceInto() != null && !drops2.contains(s.terraceInto()))
                throw new IllegalArgumentException(id + ": settlement " + s.name() + " terraces into no drop " + s.terraceInto());
        }
    }

    /** The building of that name, or null. */
    public Building building(String name) {
        for (Settlement s : settlements) for (Building b : s.buildings()) if (b.name().equals(name)) return b;
        return null;
    }

    /** The site of that name, or null. */
    public Site site(String name) {
        for (Site s : sites) if (s.name().equals(name)) return s;
        return null;
    }

    /** The drop of that name, or null. */
    public Drop drop(String name) {
        for (Drop d : drops) if (d.name().equals(name)) return d;
        return null;
    }

    /** A map cell. */
    public record Cell(int x, int y) { }

    /** A block of cells, both corners inclusive; built from any two corners. */
    public record Box(int x0, int y0, int x1, int y1) {
        public Box {
            if (x1 < x0) { int t = x0; x0 = x1; x1 = t; }
            if (y1 < y0) { int t = y0; y0 = y1; y1 = t; }
        }

        public boolean contains(int x, int y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
    }

    /** What worked ground is for; the skin decides what it is made of. */
    public enum GroundRole {
        /** A hard-standing yard (a works' concrete), laid before the roads. */
        YARD,
        /** Painted hard standing (a bay, a stand's lines), laid after the pads. */
        MARKING
    }

    /**
     * Worked ground over a box; with a {@code radius} above 0, every cell within that many cells of the box, so a
     * yard's corners are round.
     */
    public record Ground(GroundRole role, Box box, int radius) { }

    /** A landing pad: centre, half extents, the side its boats come in from, and what it is for. */
    public record Pad(Cell at, int halfWidth, int halfHeight, LandingPad.Approach approach, LandingPad.Purpose purpose) { }

    /**
     * A named site the mission reads. A site on a {@code building} takes that building's floor anchor and box once it
     * stands (whichever way the skin built it); otherwise {@code at} and {@code area} are its own.
     *
     * @param kind     the tactical node the site is published as
     * @param guard    who holds it at the start
     * @param value    its priority score
     * @param garrison how many guard it at the start
     */
    public record Site(String name, TacticalNode.Kind kind, String building, Cell at, Box area, Faction guard,
                       int value, int garrison) {
        public Site {
            if (building == null && (at == null || area == null))
                throw new IllegalArgumentException("site " + name + " needs a building or its own cell and area");
        }
    }

    /**
     * What a settlement is for, which bounds what a skin may make of it ({@code owned-maps.md}, Adaptation).
     */
    public enum SettlementRole {
        /** People live here: a town, a hamlet, a camp. A hostile world seals it. */
        TOWN,
        /** A working installation: a relay, a depot, a mine head. Its buildings are works; a hostile world seals it. */
        WORKS
    }

    /**
     * A settlement or works by program and role, never by building style.
     *
     * @param program     what it is ("relay-station", "market-town"): a name for people and the editor
     * @param lot         the ground it owns; an adaptation builds only inside it
     * @param gates       where its ways meet the lot's edge; a sealed perimeter opens its locks only here
     * @param buildings   its buildings, by name, each where the map puts it
     * @param works       its dressing in order: yards, fences, bays, parked machines
     * @param terraceInto the drop whose bank it may be terraced into on a world whose drop takes terraces (a
     *                    crevasse town); null when it may not
     */
    public record Settlement(String name, String program, SettlementRole role, Box lot, List<Cell> gates,
                             List<Building> buildings, List<Work> works, String terraceInto) {
        public Settlement {
            gates = List.copyOf(gates);
            buildings = List.copyOf(buildings);
            works = List.copyOf(works);
        }
    }

    /** How a building's plan is given. */
    public enum Form {
        /** An authored building plan ({@code BuildingStamp}) by name, at its footprint's left-bottom corner, turned. */
        STAMP,
        /** A story facility shell ({@code OpeningOperationFacilityFiller}) of a kind over a box, facing a side. */
        FACILITY
    }

    /**
     * A building, by name.
     *
     * @param plan     the {@code BuildingStamp} name (STAMP) or the {@code PointOfInterest.Kind} (FACILITY)
     * @param at       the footprint's corner (STAMP)
     * @param turns    quarter turns (STAMP)
     * @param box      the footprint (FACILITY)
     * @param frontage the side its entrance faces (FACILITY)
     * @param opposed  whether a facility owes a service entrance opposite its front
     */
    public record Building(String name, Form form, String plan, Cell at, int turns, Box box, Frontage frontage,
                           boolean opposed) { }

    /** A yard dressing step ({@code StoryServiceYard}, {@code ServiceYardTemplate}). */
    public enum WorkOp { GRAVEL, FENCE, BAY, PROP, CARGO, TEMPLATE }

    /** One dressing step: a box for GRAVEL, FENCE and BAY; a cell, an id and turns for PROP, CARGO and TEMPLATE. */
    public record Work(WorkOp op, Box box, Cell at, String id, int turns) { }

    /** A straight wall from one cell to another along a row or a column. */
    public record Wall(Cell from, Cell to) {
        public Wall {
            if (from.x() != to.x() && from.y() != to.y()) throw new IllegalArgumentException("a wall runs straight");
        }
    }

    /** A doodad set down by id. */
    public record Prop(Cell at, String id) { }

    /**
     * What a drop is in the map's design; the skin picks how it looks ({@code owned-maps.md}, Drops).
     */
    public enum DropRole {
        /** A watercourse cut through the ground: a gorge river, a canyon's wash, a crevasse, a shallow lava river. */
        RIVER,
        /** A deep split in the ground: a gorge, a crevasse, a lava rift. */
        RIFT
    }

    /**
     * A natural drop: impassable, seen across, crossed only where it is decked.
     *
     * @param path      its centre line, cells in order
     * @param width     how many cells wide it is cut
     * @param crossings path cells a footbridge decks across it, {@code crossingWidth} cells wide
     * @param depths    where the floor is deeper or shallower along it, as shares of the skin's depth band; empty
     *                  for the skin to spread over the band itself
     */
    public record Drop(String name, DropRole role, List<Cell> path, int width, List<Cell> crossings,
                       int crossingWidth, List<Depth> depths) {
        public Drop {
            path = List.copyOf(path);
            crossings = List.copyOf(crossings);
            depths = List.copyOf(depths);
            if (path.size() < 2) throw new IllegalArgumentException("drop " + name + " needs a path");
            if (width < 2) throw new IllegalArgumentException("drop " + name + " is at least two cells wide");
            if (crossingWidth < 1 && !crossings.isEmpty()) throw new IllegalArgumentException("drop " + name + " crossings need a width");
        }
    }

    /** A depth along a drop: at this cell its floor lies at {@code share} of the skin's band, 0 shallowest. */
    public record Depth(Cell at, float share) {
        public Depth {
            if (share < 0f || share > 1f) throw new IllegalArgumentException("a depth share lies in 0..1");
        }
    }

    /** What a rise is in the map's design. */
    public enum RiseRole {
        /** A bank climbing in terraces from a floor the battle is fought on. */
        BANK
    }

    /**
     * A natural rise: cells standing up out of the map, unwalkable and blocking sight, drawn in terraces.
     *
     * @param box   the cells it stands on
     * @param share how high its top stands, as a share of the skin's drop floor
     */
    public record Rise(String name, RiseRole role, Box box, float share) { }
}
