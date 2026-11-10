package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.LandingArea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ruins site's ground as a mission reads it ({@code mapgen-nouns.md}, Ruins ground): the delve from the landing area at the
 * map's west end to the vault at its east end, the layers the route crosses, their side rooms and landing courts,
 * and the sites of the ruins' works ({@code ruins-expedition.md}, Works). Every place carries a stable name; {@link #area}
 * answers any of them, so a mission can say "the second layer" or "the power hall" without searching the map.
 *
 * <p>Published names, in order:
 * <ul>
 *   <li>{@code ruins.landing}: the cleared landing ground; its arrival areas are {@link #landing}, and are also the
 *       map's own {@code landingAreas}.</li>
 *   <li>{@code ruins.vault}: the vault, with its door, the approach cell outside it and its one source cell.</li>
 *   <li>{@code ruins.layer.N} (N from 1, nearest the landing first): a layer's holdable ground, its wall line and
 *       the ground behind it up to the next layer.</li>
 *   <li>{@code ruins.layer.N.way.M}: the ways through layer N, from the map's low y edge to its high one.</li>
 *   <li>{@code ruins.layer.N.room.M}: the small ruined rooms off layer N.</li>
 *   <li>{@code ruins.layer.N.court}: the open landing court of layer N, for every layer but the first.</li>
 *   <li>{@code ruins.works.power-hall}, {@code ruins.works.junction.N} (one per layer), {@code ruins.works.records}
 *       and {@code ruins.works.collapse} (the choked short way, itself also a way of its layer).</li>
 *   <li>{@code ruins.interior}: the roofed megastructure at the vault's end, or the bunker round the vault on
 *       scattered ground; {@code ruins.interior.chamber.M}, its unnamed-purpose chambers;
 *       {@code ruins.interior.entrance.M}, the open apron outside each entrance.</li>
 * </ul>
 * Lift points are the landing area and every court ({@link #liftPoints}). Courts are not in the map's
 * {@code landingAreas}: the company's own lift comes down on the landing area, and a court is somewhere the boat
 * is called to.
 */
public record RuinsDelve(int layerCount, Area landingGround, List<LandingArea> landing, Vault vault,
                         List<Layer> layers, PowerHall powerHall, List<Junction> junctions,
                         RecordsTerminal records, Collapse collapse, Interior interior) {

    public static final String LANDING = "ruins.landing";
    public static final String VAULT = "ruins.vault";
    public static final String POWER_HALL = "ruins.works.power-hall";
    public static final String RECORDS = "ruins.works.records";
    public static final String COLLAPSE = "ruins.works.collapse";
    public static final String INTERIOR = "ruins.interior";

    public RuinsDelve {
        landing = List.copyOf(landing);
        layers = List.copyOf(layers);
        junctions = List.copyOf(junctions);
    }

    /** The name of layer {@code index} (0-based, nearest the landing first). */
    public static String layerName(int index) { return "ruins.layer." + (index + 1); }

    /** The name of junction {@code index} (0-based). */
    public static String junctionName(int index) { return "ruins.works.junction." + (index + 1); }

    /** One rectangle of cells, inclusive; {@code bottom} is the low y and {@code top} the high y, as on {@link LandingArea}. */
    public record Area(int left, int bottom, int right, int top) {
        public Area {
            if (right < left || top < bottom) throw new IllegalArgumentException("empty area");
        }
        public boolean contains(int x, int y) { return x >= left && x <= right && y >= bottom && y <= top; }
        public int centreX() { return (left + right) / 2; }
        public int centreY() { return (bottom + top) / 2; }
        public int width() { return right - left + 1; }
        public int height() { return top - bottom + 1; }
    }

    /** One cell. */
    public record Cell(int x, int y) {}

    /**
     * The vault: one hardened suite, an antechamber and the vault room behind an inner door. {@code door} is its
     * one outer door, {@code approach} the open cell straight out from it, and {@code source} the cell the breach
     * is held on, inside the vault room.
     */
    public record Vault(Area footprint, Cell door, Cell innerDoor, Cell approach, Cell source) {}

    /** What a layer is made of. */
    public enum LayerKind {
        /** A street choked by fallen frontage, with rubble-strewn gaps. */
        COLLAPSED_STREET,
        /** A standing wall with breaches torn through it. */
        BREACHED_WALL,
        /** A built barrier with a gate span in it and a side way round it. */
        SEALED_GATE
    }

    /** What a way through a layer is. Every kind but {@link #COLLAPSE} is open ground on the finished map. */
    public enum WayKind {
        /** A gap in a collapsed street. */
        GAP,
        /** A breach in a standing wall. */
        BREACH,
        /** A sealed gate's span: open ground until the works shut it ({@code ruins-expedition.md}, Works). */
        GATE,
        /** The way round a sealed gate. */
        SIDE,
        /** The short way, choked by a breachable run of rubble wall; a charge clears it. */
        COLLAPSE
    }

    /**
     * One way through a layer: {@code span}, its cells on the layer's wall line (one column, a few rows), and an
     * open cell either side of it, {@code near} on the landing's side and {@code far} on the vault's.
     */
    public record Way(String name, WayKind kind, Area span, Cell near, Cell far) {
        /** Whether the way is open ground now; a collapse is closed until it is cleared. */
        public boolean open() { return kind != WayKind.COLLAPSE; }
        /** Rows across the way. */
        public int width() { return span.height(); }
    }

    /** A small ruined room off a layer, entered by one door; where field caches and sealed stores stand. */
    public record SideRoom(String name, Area footprint, Cell door, Cell interior) {}

    /** An open court in a layer where the boat can set down: a lift point once held. */
    public record Court(String name, Area ground, LandingArea area) {}

    /** The plant's stages a hall serves, in the order its product went through them ({@code ruins-expedition.md}). */
    public enum PlantStage {
        /** Receiving docks: where stock came in. */
        INTAKE,
        /** Line halls: where the product was made. */
        PRODUCTION,
        /**
         * A forge works' foundry, in the outer band's first line hall's slot: crucibles, pour channels and casting
         * beds of melt ({@code ruins-expedition.md}, A forge works has a foundry).
         */
        FOUNDRY,
        /**
         * A themed plant's works hall ({@link WorksHall}), laid in the outer band in place of its line halls: the
         * mega hall its product was made in, laid from its process.
         */
        WORKS,
        /** Rack bays: where it waited to ship. */
        STORAGE
    }

    /**
     * One of the plant's halls inside the megastructure: a large interior floor with its stage's furniture. Every hall
     * is also among the interior's chambers, so the delve laws hold it.
     */
    public record PlantHall(String name, PlantStage stage, Area footprint) {}

    /**
     * One band of ruined ground the route must get through. {@code ground} is the holdable band, from just in
     * front of its wall line to the next layer's; {@code lineLeft..lineRight} is the column range the wall line
     * jogs through.
     */
    public record Layer(String name, int index, LayerKind kind, Area ground, int lineLeft, int lineRight,
                        List<Way> ways, List<SideRoom> rooms, Court court) {
        public Layer {
            ways = List.copyOf(ways);
            rooms = List.copyOf(rooms);
        }

        /** The ways that are open ground now. */
        public List<Way> openWays() {
            List<Way> open = new ArrayList<>();
            for (Way way : ways) if (way.open()) open.add(way);
            return open;
        }
    }

    /** The site's generator hall: its console is where infantry restart it. In layer {@code layer} (0-based). */
    public record PowerHall(String name, Area footprint, Cell door, Cell console, int layer) {}

    /** A junction box on a layer's wall line: {@code stand} is the cell infantry work it from. */
    public record Junction(String name, Cell stand, int layer) {}

    /** The records terminal, in its own small office: {@code console} is where infantry read it. */
    public record RecordsTerminal(String name, Area footprint, Cell door, Cell console, int layer) {}

    /**
     * The open ground outside an entrance to the interior ({@code ruins-expedition.md}, The entrance and the
     * pull-out point): {@code door} is the entrance it fronts, {@code ground} the apron the company holds to take
     * it, under open sky, and {@code lift} the arrival area on it the boat comes to once it is the entry. The lift
     * is not among {@link #liftPoints}: the mission makes it one when the entrance is taken.
     */
    public record Apron(String name, Area door, Area ground, LandingArea lift) {}

    /** How a leaf door stands in the dead plant ({@code ruins-expedition.md}, The ruins' doors are leaf doors). */
    public enum DoorStance {
        /** Still powered: it parts for whoever comes through and closes behind them. */
        ANSWERS,
        /** Stuck part-open where its power died: its leaves stand in the way's end cells, which are shut. */
        DEAD,
        /** Torn open long ago: one leaf lies on the floor beside the way, the other jammed in its track. */
        FORCED,
        /**
         * Shut and holding: its leaves meet across the way, which is wall, its lamp dark. A security bulkhead stands
         * so until the hunt opens it ({@code RuinsDoorStates}).
         */
        SEALED,
        /**
         * Torn out wider than it was built, jambs and all: no leaf stands, one lies on the floor, rubble at both new
         * ends. A derelict hall frame leaves a door so when it rams one too narrow for it ({@code RuinsDoorStates}).
         */
        TORN
    }

    /**
     * A sliding leaf door across a plant hall's join or a way in from open ground: two leaves parting from a seam
     * ({@code RoomDoor}), across the cells of {@code span}, which runs along x when {@code alongX}. A cargo door's
     * leaves telescope, two each way. Its passage is the grid's: a dead door's end cells are shut at generation.
     */
    public record LeafDoor(Area span, boolean alongX, boolean cargo, DoorStance stance) {
        public LeafDoor {
            if (span == null || stance == null) throw new IllegalArgumentException("a leaf door needs a span and a stance");
        }
        /** Cells along the door. */
        public int length() { return alongX ? span.width() : span.height(); }
    }

    /** The collapse across the short way of layer {@code layer}; the layer's open ways are the long way round. */
    public record Collapse(String name, int layer, Way shortWay) {}

    /**
     * The roofed part of the site: a megastructure filling the map's east end from the wall line of layer
     * {@code firstLayer} on, its layers' bands carved into halls, corridors and chambers, with the vault's
     * forecourt as a roofed hall; or, when {@code firstLayer} is the layer count, only the bunker round the vault.
     * {@code entrances} are the ways in from open ground (the first interior layer's open ways, or the bunker's
     * door). A court inside is a light well open to the sky, so every lift point is under open sky.
     */
    public record Interior(String name, Area footprint, int firstLayer, List<Area> entrances, List<Apron> aprons,
                           List<Area> chambers, List<PlantHall> halls, List<LeafDoor> doors) {
        public Interior {
            entrances = List.copyOf(entrances);
            aprons = List.copyOf(aprons);
            chambers = List.copyOf(chambers);
            halls = halls == null ? List.of() : List.copyOf(halls);
            doors = doors == null ? List.of() : List.copyOf(doors);
        }

        /** An interior whose ways in and joins carry no doors. */
        public Interior(String name, Area footprint, int firstLayer, List<Area> entrances, List<Apron> aprons,
                        List<Area> chambers, List<PlantHall> halls) {
            this(name, footprint, firstLayer, entrances, aprons, chambers, halls, List.of());
        }

        /** An interior without plant halls. */
        public Interior(String name, Area footprint, int firstLayer, List<Area> entrances, List<Apron> aprons,
                        List<Area> chambers) {
            this(name, footprint, firstLayer, entrances, aprons, chambers, List.of());
        }

        /** An interior with no aprons published, for a delve built by hand. */
        public Interior(String name, Area footprint, int firstLayer, List<Area> entrances, List<Area> chambers) {
            this(name, footprint, firstLayer, entrances, List.of(), chambers);
        }

        /** Whether layer {@code index} (0-based) is inside. */
        public boolean contains(int layerIndex) { return layerIndex >= firstLayer; }

        /** The name of chamber {@code index} (0-based). */
        public static String chamberName(int index) { return INTERIOR + ".chamber." + (index + 1); }

        /** The name of entrance {@code index}'s apron (0-based). */
        public static String apronName(int index) { return INTERIOR + ".entrance." + (index + 1); }
    }

    /** Where the haul may board: the landing area's arrival areas, then each court's, nearest the landing first. */
    public List<LandingArea> liftPoints() {
        List<LandingArea> out = new ArrayList<>(landing);
        for (Layer layer : layers) if (layer.court() != null) out.add(layer.court().area());
        return out;
    }

    /** Every published place by name, in the order the class documentation lists them. */
    public Map<String, Area> places() {
        Map<String, Area> out = new LinkedHashMap<>();
        out.put(LANDING, landingGround);
        out.put(VAULT, vault.footprint());
        for (Layer layer : layers) {
            out.put(layer.name(), layer.ground());
            for (Way way : layer.ways()) out.put(way.name(), way.span());
            for (SideRoom room : layer.rooms()) out.put(room.name(), room.footprint());
            if (layer.court() != null) out.put(layer.court().name(), layer.court().ground());
        }
        out.put(POWER_HALL, powerHall.footprint());
        for (Junction junction : junctions) {
            out.put(junction.name(), new Area(junction.stand().x(), junction.stand().y(),
                    junction.stand().x(), junction.stand().y()));
        }
        out.put(RECORDS, records.footprint());
        out.put(COLLAPSE, collapse.shortWay().span());
        if (interior != null) {
            out.put(INTERIOR, interior.footprint());
            for (int k = 0; k < interior.chambers().size(); k++) {
                out.put(Interior.chamberName(k), interior.chambers().get(k));
            }
            for (Apron apron : interior.aprons()) out.put(apron.name(), apron.ground());
        }
        return out;
    }

    /** The area of a published place, or null when this delve has none by that name. */
    public Area area(String name) {
        return places().get(name);
    }
}
