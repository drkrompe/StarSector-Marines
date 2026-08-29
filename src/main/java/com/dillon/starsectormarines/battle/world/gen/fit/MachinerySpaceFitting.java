package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A machinery space as flats of plant separated by working alleys, with the
 * control station at the near end and a standing list of defects.
 *
 * <p>An engine room is not a warehouse of one machine. It has a <b>hierarchy</b>:
 * the heavy plant against the outboard bulkhead, the auxiliaries that serve it
 * ranked inboard, pipework and loose gear threaded through the gaps between
 * them, and a board somebody stands a watch at. Ranking one fixture down both
 * sides of the room the way a berth is ranked gives none of that — it gives a
 * lattice of identical props, which is what the two machinery purposes used to
 * come out as, and the defect it is hard to name from the plan and impossible to
 * miss standing in the room.
 *
 * <p>The hierarchy is carried by the art as well as by the arrangement. A
 * machine is a <b>lead</b> — its own piece, which for the heavy plant is several
 * cells of it — standing on the working face, with 1x1 <b>packing</b> crowded
 * round it for the bulk and the ancillaries. A run of five tanks with a tank at
 * its face is a rank; five cells of tankage with a reactor housing set into it
 * is a machine. The drive room goes further and has a <b>centrepiece</b>: one
 * turbine set amidships that is not one of a rank at all.
 *
 * <p>The arrangement is the same for the drive and for the auxiliary plant
 * forward of it, because the difference between those two rooms is what
 * machinery is in them rather than how machinery is laid out. So the flats and
 * the alleys are written once and the catalogue of plant is handed in; see
 * {@link #driveRoom()} and {@link #auxiliaryPlant()}.
 *
 * <p>Three things are published, and the distinction between them is what makes
 * the room somewhere people are rather than somewhere they pass through:
 *
 * <ul>
 *   <li>{@link Affordance#TEND} at every sound machine — the plant kept running.
 *   <li>{@link Affordance#WATCH} at the control station, because a
 *       watchkeeper stands at a board and not at a tank. A room with plant and
 *       no board is a room where the machinery runs itself.
 *   <li>{@link Affordance#REPAIR} at the machines on the list. A ship of any
 *       size always has one, and it is what keeps a trade moving around the hull
 *       instead of circling one compartment.
 * </ul>
 *
 * <p>All three counts follow from the room's own size. A longer flat holds more
 * machines; more deck earns another board; a longer plant run carries a longer
 * list. Nothing records a quota separately, which is the same rule the rest of
 * the fittings keep: capacity <em>is</em> what was placed.
 *
 * <p>Which machines are defective is drawn from where the room sits on the deck
 * and where the machine sits in the room, never from a live random. Generation
 * has to replay identically from a seed, and a fitting that reached for an
 * unseeded source would make the same ship come out differently twice.
 */
public final class MachinerySpaceFitting implements RoomFitting {

    /**
     * One kind of machine: the piece that is its front, the 1x1 art packed round
     * it, how much of a flat it takes, and what is done at it when nothing is
     * wrong with it.
     *
     * <p>Lead and packing are separate because a machine read from above is
     * mostly bulk with one thing worth walking to. Marking a single lead is what
     * makes a run of machines read as plant with fronts on it, rather than as a
     * grid of props that happen to be touching.
     *
     * @param run least cells along a flat this machine takes. A floor, not a
     *     size: a lead drawn wider than this widens the machine to fit it, so a
     *     piece of art is never cut in half by the number written here
     */
    public record Plant(String lead, String packing, int run, Affordance work) {}

    /** Deck between one flat of plant and the next. Where a hand stands to work. */
    private static final int ALLEY = 2;
    /** Least depth of the flat against the outboard bulkhead, which carries the heavy plant. */
    private static final int MAIN_DEPTH = 4;
    /** Least depth of an auxiliary flat, which carries the machinery serving the heavy plant. */
    private static final int AUX_DEPTH = 3;
    /**
     * Athwartships run at the near end, joining every alley to every other one
     * and to the hatch.
     *
     * <p>Without it the alleys are parallel dead ends, each reachable only by a
     * door cut into its own end, and a machinery space is a room somebody walks
     * a circuit of.
     */
    private static final int CROSS = 2;
    /** Cells of pipework and loose gear between one machine and the next. */
    private static final int SERVICE_GAP = 1;
    /** Narrowest thing still worth calling a machine rather than a fitting. */
    private static final int MIN_MACHINE = 2;
    /** Cells at the after end of each flat given over to ready-use spares. */
    private static final int STORES_WIDTH = 2;
    /**
     * Cells of deck one watchkeeper's board answers for.
     *
     * <p>A number rather than a constant count, so the drive room — the largest
     * compartment aft — stands a bigger watch than the auxiliary plant does
     * without either being told how many people it holds.
     */
    private static final int DECK_PER_BOARD = 250;
    /** One machine in this many is on the defect list. */
    private static final int DEFECT_SHARE = 4;

    /**
     * Loose gear, threaded through the gaps between machines.
     *
     * <p>Scenery, and deliberately. The gap is where pipework crosses from one
     * machine to the next and where whatever nobody has dealt with yet gets
     * pushed; it is packed too tightly to stand in, and the work is on the
     * machines either side of it.
     */
    private static final String[] LOOSE = {
            "doodad.industrial-pipe-bundle",
            "doodad.industrial-cable-reel",
            "doodad.industrial-drum-cluster",
            "doodad.industrial-scrap-pile",
            "doodad.industrial-dumpster" };

    /** Ready-use spares at the after end of a flat, where the plant run runs out. */
    private static final String STORES = "doodad.industrial-crate-stack";
    private static final String STORES_MATE = "doodad.industrial-pallet-stack";

    /**
     * The control station: the watchkeeping boards, the terminal beside them,
     * and the racks they read from behind.
     *
     * <p>The board is the engineer's own instrument panel rather than the
     * military command console the bridge uses. A watch on the plant is kept at
     * a mimic board with the throttles on it, and borrowing the tactical console
     * for it said the engine room was run by somebody fighting the ship.
     */
    private static final String BOARD = "doodad.industrial-engine-control-board";
    private static final String TERMINAL = "doodad.industrial-control-console";
    private static final String STATION_BACK = "doodad.office-server-rack";

    /**
     * The deck of a machinery space, laid before anything stands on it.
     *
     * <p>Gratings are what a machinery flat is walked on, and they do more for
     * how these two rooms read than any single fixture: they say the empty deck
     * between the plant is a working alley rather than floor nobody got round to
     * furnishing. The hazard band goes where deck meets the heavy plant, which
     * is the one edge worth marking.
     *
     * <p>Doodads draw in the order they are recorded, so the deck is laid first
     * or it is laid over the machinery.
     */
    private static final String[] PLATE = { "doodad.fl-grate-1", "doodad.fl-grate-2" };
    private static final String HAZARD = "doodad.fl-striped-yellow";

    private final RoomPurpose purpose;
    private final List<Plant> heavy;
    private final Plant centrepiece;
    private final List<Plant> auxiliary;

    /**
     * @param centrepiece the one machine that is not one of a rank, laid
     *     amidships in the heavy flat, or null for a room whose plant is all of
     *     a scale
     */
    public MachinerySpaceFitting(RoomPurpose purpose, List<Plant> heavy,
                                 Plant centrepiece, List<Plant> auxiliary) {
        if (heavy.isEmpty() || auxiliary.isEmpty()) {
            throw new IllegalArgumentException("a machinery space needs plant to hold");
        }
        this.purpose = purpose;
        this.heavy = List.copyOf(heavy);
        this.centrepiece = centrepiece;
        this.auxiliary = List.copyOf(auxiliary);
    }

    /**
     * The drive itself, hard against the transom: the turbine set amidships with
     * reactor housings and pump sets ranked either side of it, and the
     * switchboard, the standby sets and the purifiers inboard.
     *
     * <p>The turbine is the centrepiece because a main engine is not one of
     * several. Making it a catalogue entry like the rest put three of them down
     * a single flat, which is a different and slightly worse lie than the rank
     * of tanks it replaced — a ship with three main engines side by side.
     */
    public static MachinerySpaceFitting driveRoom() {
        return new MachinerySpaceFitting(RoomPurpose.ENGINE_ROOM,
                List.of(
                        new Plant("doodad.industrial-reactor-housing",
                                "doodad.industrial-fluid-tank", 5, Affordance.TEND),
                        new Plant("doodad.industrial-pump-set",
                                "doodad.industrial-generator", 4, Affordance.TEND)),
                new Plant("doodad.industrial-turbine-set",
                        "doodad.industrial-generator", 5, Affordance.TEND),
                List.of(
                        // The switchboard is read rather than laid hands on, so
                        // it publishes a look at a gauge and not a shift's work.
                        new Plant("doodad.industrial-switchboard",
                                STATION_BACK, 3, Affordance.READOUT),
                        new Plant("doodad.industrial-pump-set",
                                "doodad.industrial-generator", 3, Affordance.TEND),
                        new Plant("doodad.industrial-drum-cluster",
                                "doodad.industrial-fluid-tank", 3, Affordance.TEND),
                        new Plant(TERMINAL,
                                "doodad.industrial-machine-tool", 2, Affordance.TEND)));
    }

    /**
     * The auxiliary plant that keeps a ship alive rather than moving: pump sets
     * and an auxiliary reactor on the outboard flat, switchgear and the
     * machinery-space bench inboard.
     *
     * <p>Distinct from the drive room in what it holds and not in how it is
     * arranged, which is why the two share a fitting. It has no centrepiece —
     * the turbine belongs to the drive — and a part gets made at the bench here,
     * which is the one job the drive room has no business offering.
     */
    public static MachinerySpaceFitting auxiliaryPlant() {
        return new MachinerySpaceFitting(RoomPurpose.PRODUCTION_FLOOR,
                List.of(
                        new Plant("doodad.industrial-pump-set",
                                "doodad.industrial-generator", 4, Affordance.TEND),
                        new Plant("doodad.industrial-reactor-housing",
                                "doodad.industrial-fluid-tank", 4, Affordance.TEND)),
                null,
                List.of(
                        new Plant("doodad.industrial-switchboard",
                                STATION_BACK, 3, Affordance.READOUT),
                        new Plant("doodad.industrial-machine-tool",
                                "doodad.industrial-machine-tool", 3, Affordance.FABRICATE),
                        new Plant("doodad.industrial-drum-cluster",
                                "doodad.industrial-fluid-tank", 3, Affordance.TEND)));
    }

    @Override
    public RoomPurpose purpose() {
        return purpose;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        List<Flat> flats = flats(floor, across);
        // A compartment too shallow to hold one flat and the alley beside it is
        // left bare rather than filled with something generic: an empty room is
        // honest about not fitting, and one scattered with drums is not.
        if (flats.isEmpty()) return;

        // Circulation first, and all of it. Everything that is not a flat of
        // plant is deck somebody walks on, so reserving it before a single
        // machine goes down is the whole of why the after end of a machinery
        // space stays reachable from its hatch.
        reserveAlleys(floor, flats, along, across);
        for (Doorway door : floor.localDoors()) {
            stubFromDoor(floor, door, flats, along, across);
        }
        plateAlleys(floor, flats, along, across);

        int spares = Math.max(CROSS, along - STORES_WIDTH);
        int boards = boards(floor, along, across, spares);
        int station = stationWidth(floor, boards);
        Flat control = flats.get(flats.size() - 1);

        Run run = layOut(floor, flats, control, station, spares);
        Set<Integer> listed = defects(run.machines(),
                floor.room().originX(), floor.room().originY());
        // Spares before plant, so a run that stops a cell short of the nook
        // does not leave a hole between the two. The nook takes the whole tail
        // of its flat, however long the last machine left it.
        for (int i = 0; i < flats.size(); i++) {
            layStores(floor, flats.get(i), run.tails()[i], along);
        }
        for (int i = 0; i < run.machines().size(); i++) {
            install(floor, run.machines().get(i), listed.contains(i), i);
        }
        layStation(floor, control, boards);
    }

    /**
     * One flat of plant: where it starts, how deep it is, and which of its rows
     * faces the alley people work from.
     *
     * <p>The face is carried rather than derived at use. The outboard flat is
     * worked from the alley abaft it and every flat inboard of that is worked
     * from the alley ahead of it, which is a fact about how the flats were laid
     * down — recomputing it from a row index later is how a machine ends up
     * publishing its work into its own bulk.
     */
    private record Flat(int from, int depth, boolean heavy, int face, boolean faceHigh) {

        /**
         * The first row a piece of the given depth occupies when it is stood on
         * this flat's face.
         *
         * <p>A machine reaches <em>back</em> into its flat from the side people
         * are on, whichever side that is. Anchoring every lead at the face row
         * instead would push the outboard flat's machines out through the alley
         * they are worked from.
         */
        int leadFrom(int leadDepth) {
            return faceHigh ? face - leadDepth + 1 : face;
        }
    }

    /** One machine standing in a flat: which flat, where along it, and what it is. */
    private record Machine(Flat flat, int along, int width, Plant plant) {}

    /** The plant a room came out holding, and the column each flat's run ran out at. */
    private record Run(List<Machine> machines, int[] tails) {}

    /**
     * How much of the canonical frame one piece of art covers, as
     * {@code {along, across}}.
     *
     * <p>Read off the registry rather than restated here. A footprint is a
     * property of the art, and a number copied into a fitting is a number that
     * goes stale in silence — the room still fills, and simply fills wrong.
     *
     * <p>Swapped for a quarter-turned compartment, which is the part that is
     * easy to get wrong and impossible to see afterwards. Doodads are placed
     * unrotated, so a piece drawn five cells long lies along the <em>deck's</em>
     * x axis whatever the room did; in a turned room that axis is this fitting's
     * across. Without the swap a turbine reserves five cells of a flat, is drawn
     * lying athwart it, and the next machine is laid straight through it.
     */
    private static int[] span(RoomFloor floor, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        int x = def == null ? 1 : def.footprintCellsX;
        int y = def == null ? 1 : def.footprintCellsY;
        return floor.pose().upright() ? new int[]{ x, y } : new int[]{ y, x };
    }

    /** Cells along a flat one machine takes: its declared run, or its lead if that is wider. */
    private static int machineWidth(RoomFloor floor, Plant plant) {
        return Math.max(plant.run(), span(floor, plant.lead())[0]);
    }

    /** The deepest any of these machines reaches back from its face. */
    private static int reach(RoomFloor floor, int least, List<Plant> catalogue) {
        int deepest = least;
        for (Plant plant : catalogue) deepest = Math.max(deepest, span(floor, plant.lead())[1]);
        return deepest;
    }

    /**
     * Band the room's depth into flats, alleys falling out as what is left
     * between them.
     *
     * <p>The outboard flat is deeper than the rest because the heavy plant is
     * bigger than what serves it, and that difference is most of what makes the
     * room read as having a hierarchy rather than a repeat.
     *
     * <p>Both depths are <b>sized from the plant that has to stand in them</b>
     * rather than fixed. A flat shallower than its own machinery is a flat whose
     * machinery is quietly refused, and because a quarter-turned compartment
     * swaps which way a piece of art reaches, a fixed depth would hold a turbine
     * in half the rooms it was placed in and drop it in the other half — the
     * worst kind of defect, since the plan still looks right.
     */
    private List<Flat> flats(RoomFloor floor, int across) {
        List<Flat> flats = new ArrayList<>();
        int mainReach = reach(floor, MAIN_DEPTH, heavy);
        if (centrepiece != null) {
            mainReach = Math.max(mainReach, span(floor, centrepiece.lead())[1]);
        }
        // The inboard flat carries the station as well as its plant, so the
        // board is sized into it: a flat one cell too shallow for the board
        // leaves the compartment with plant, alleys, and no watch.
        int auxReach = Math.max(reach(floor, AUX_DEPTH, auxiliary),
                Math.max(span(floor, BOARD)[1], span(floor, TERMINAL)[1]));
        // The outboard flat gives up depth before it gives up its alley: plant
        // with nowhere to stand beside it is plant nobody can work.
        int mainDepth = Math.min(mainReach, across - ALLEY);
        if (mainDepth < 1) return flats;

        int count = 1 + (across - mainDepth) / (auxReach + ALLEY);
        if (count == 1) {
            // One flat and the deck abaft it. The rule below wants an alley
            // ahead of every flat after the first, and there is no flat after
            // the first here — so this one is worked from behind instead.
            flats.add(new Flat(0, mainDepth, true, mainDepth - 1, true));
            return flats;
        }

        // Whatever the flats do not use is alley, shared out evenly rather than
        // left in one lump at the far bulkhead. A machinery space that banded
        // itself to a fixed pitch ended with three rows of deck behind its last
        // flat that nothing stood on, nothing was worked from, and nothing
        // explained — an open region with no argument for being open.
        int spare = across - mainDepth - (count - 1) * auxReach;
        int alley = spare / (count - 1);
        int wider = spare % (count - 1);

        flats.add(new Flat(0, mainDepth, true, mainDepth - 1, true));
        int cursor = mainDepth;
        for (int index = 1; index < count; index++) {
            cursor += alley + (index <= wider ? 1 : 0);
            // The inboard flat runs to the bulkhead, because there is nothing
            // behind it to reach. Every flat past the first is worked from the
            // alley ahead of it either way.
            int depth = index == count - 1 ? across - cursor : auxReach;
            flats.add(new Flat(cursor, depth, false, cursor, false));
            cursor += depth;
        }
        return flats;
    }

    /** Everything that is not a flat, plus the athwartships run joining them. */
    private void reserveAlleys(RoomFloor floor, List<Flat> flats, int along, int across) {
        boolean[] plant = plantRows(flats, across);
        for (int row = 0; row < across; row++) {
            if (!plant[row]) reserve(floor, 0, row, along, 1);
        }
        reserve(floor, 0, 0, Math.min(CROSS, along), across);
    }

    private static boolean[] plantRows(List<Flat> flats, int across) {
        boolean[] plant = new boolean[across];
        for (Flat flat : flats) {
            for (int row = flat.from(); row < flat.from() + flat.depth(); row++) {
                if (row >= 0 && row < across) plant[row] = true;
            }
        }
        return plant;
    }

    /**
     * Join a hatch to the nearest alley, one cell wide.
     *
     * <p>A hatch on the near bulkhead already opens onto the athwartships run,
     * so nothing is spent on it. This is for the hatch the deck could only cut
     * halfway down a long side, and the cost is a single machine's worth of
     * flat rather than the whole band the fill would otherwise have to give up.
     */
    private void stubFromDoor(RoomFloor floor, Doorway door,
                              List<Flat> flats, int along, int across) {
        int[] canonical = floor.toCanonical(door.x(), door.y());
        int column = Math.max(0, Math.min(along - 1, canonical[0]));
        int row = Math.max(0, Math.min(across - 1, canonical[1]));
        Flat blocking = null;
        for (Flat flat : flats) {
            if (row >= flat.from() && row < flat.from() + flat.depth()) blocking = flat;
        }
        if (blocking == null) return;      // the hatch already opens onto an alley
        int from = Math.min(row, blocking.from());
        int to = Math.max(row, blocking.from() + blocking.depth() - 1);
        reserve(floor, column, from, 1, to - from + 1);
    }

    /** Lay the gratings, and the hazard band where the alley meets the heavy plant. */
    private void plateAlleys(RoomFloor floor, List<Flat> flats, int along, int across) {
        boolean[] plant = plantRows(flats, across);
        Flat main = flats.get(0);
        int hazard = main.from() + main.depth();
        for (int row = 0; row < across; row++) {
            for (int column = 0; column < along; column++) {
                // The athwartships run is deck wherever it crosses, including
                // the columns it cuts through a flat. Plating only the rows
                // between flats leaves the way in bare and the room reads as
                // gratings that stop short of its own hatch.
                if (plant[row] && column >= CROSS) continue;
                int[] cell = floor.toLocal(column, row);
                floor.pave(cell[0], cell[1],
                        row == hazard ? HAZARD : PLATE[(column + row) & 1]);
            }
        }
    }

    /**
     * Walk every flat and decide what stands in it, without placing anything.
     *
     * <p>Separated from the placing because the defect list is a property of the
     * whole run: a room's list is a proportion of its plant, so the plant has to
     * be known before any of it can be called sound.
     */
    private Run layOut(RoomFloor floor, List<Flat> flats, Flat control,
                       int station, int spares) {
        List<Machine> machines = new ArrayList<>();
        int[] tails = new int[flats.size()];
        int centre = centreOf(floor, spares);
        for (int index = 0; index < flats.size(); index++) {
            Flat flat = flats.get(index);
            List<Plant> catalogue = flat.heavy() ? heavy : auxiliary;
            int cursor = CROSS + (flat == control ? station + SERVICE_GAP : 0);
            // Each flat starts its catalogue at a different entry, so a room
            // deep enough for three of them does not repeat one run three
            // times — which is the failure this whole fitting exists to undo,
            // reintroduced one axis over.
            int kind = index;
            boolean centred = false;
            while (spares - cursor >= MIN_MACHINE) {
                boolean takeCentre = flat.heavy() && !centred && centre >= 0
                        && cursor >= centre && spares - cursor >= machineWidth(floor, centrepiece);
                Plant plant = takeCentre
                        ? centrepiece
                        : catalogue.get(Math.floorMod(kind++, catalogue.size()));
                centred |= takeCentre;
                int width = Math.min(machineWidth(floor, plant), spares - cursor);
                machines.add(new Machine(flat, cursor, width, plant));
                cursor += width + SERVICE_GAP;
            }
            tails[index] = cursor;
        }
        return new Run(machines, tails);
    }

    /**
     * Where the heavy flat's run has to have reached before the centrepiece is
     * laid, or -1 for a room with no centrepiece or no room for one.
     *
     * <p>The machine goes down at the cursor rather than at this mark, so the
     * run stays packed and lands the centrepiece as near amidships as tight
     * packing allows. Holding the slot open exactly would put a gap of bare
     * flat in front of the largest machine in the compartment, which reads as a
     * missing machine rather than as a centred one.
     */
    private int centreOf(RoomFloor floor, int spares) {
        if (centrepiece == null) return -1;
        int width = machineWidth(floor, centrepiece);
        int run = spares - CROSS;
        return run < width ? -1 : CROSS + (run - width) / 2;
    }

    /**
     * Which machines are on the list.
     *
     * <p>Drawn from where the room sits on the deck and where the machine sits
     * in the room, so two engine rooms in the same hull carry different defects
     * and the same hull generated twice carries the same ones. A live random
     * here would make a seed stop meaning a ship.
     *
     * <p>The list never covers the whole run. A machinery space where every
     * machine is broken is not a ship with a list; it is a wreck, and it would
     * also leave the room publishing no ordinary tending at all.
     */
    private static Set<Integer> defects(List<Machine> machines, int originX, int originY) {
        if (machines.isEmpty()) return Set.of();
        int quota = Math.min(machines.size() - 1,
                Math.max(1, machines.size() / DEFECT_SHARE));
        List<Integer> order = new ArrayList<>(machines.size());
        for (int i = 0; i < machines.size(); i++) order.add(i);
        // Sorted rather than sampled, so the list is exactly as long as the
        // quota says. Ties fall back on the index, which List.sort keeps stable.
        order.sort(Comparator.comparingInt(i -> scramble(originX, originY,
                machines.get(i).along() * 31 + machines.get(i).flat().from())));
        return new HashSet<>(order.subList(0, Math.max(0, quota)));
    }

    /** A cheap avalanche of three ints, so neighbouring machines do not sort together. */
    private static int scramble(int a, int b, int c) {
        int h = a * 0x9E3779B9 + b * 0x85EBCA6B + c * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2545F491;
        return h ^ (h >>> 13);
    }

    /**
     * One machine and the gap of pipework abaft it: the lead on its working
     * face, packing crowded into whatever the lead left, and the job at the
     * lead.
     *
     * <p>The lead goes down <b>first</b> and the packing is simply allowed to be
     * refused where it stands. Working out which cells a multi-cell piece will
     * cover and skipping them is the same computation done twice, and the second
     * copy is the one that drifts: a footprint changed in the atlas would leave
     * a fitting confidently reserving the wrong rectangle.
     *
     * <p>A defect displaces the ordinary work rather than joining it. There is
     * one place to stand at a machine, and a fitter with a panel off is standing
     * in it — publishing both would give the compartment twice the hands it has
     * room for and would say the machine is being tended and stripped at once.
     */
    private void install(RoomFloor floor, Machine machine, boolean listed, int index) {
        Flat flat = machine.flat();
        Plant plant = machine.plant();
        int[] lead = span(floor, plant.lead());
        placeSpanning(floor, machine.along() + (machine.width() - lead[0]) / 2,
                flat.leadFrom(lead[1]), lead[0], lead[1], plant.lead(),
                listed ? Affordance.REPAIR : plant.work());
        for (int step = 0; step < machine.width(); step++) {
            for (int row = flat.from(); row < flat.from() + flat.depth(); row++) {
                place(floor, machine.along() + step, row, plant.packing(), null);
            }
        }
        int gap = machine.along() + machine.width();
        for (int row = flat.from(); row < flat.from() + flat.depth(); row += 2) {
            place(floor, gap, row, LOOSE[Math.floorMod(index + row, LOOSE.length)], null);
        }
    }

    /**
     * Ready-use spares where a flat's plant run runs out.
     *
     * <p>Published as stowage, which is work between two points rather than at
     * one — so it is worth having only because a machinery space has several
     * flats and therefore several places to carry a part between.
     */
    private void layStores(RoomFloor floor, Flat flat, int tail, int along) {
        for (int column = tail; column < along; column++) {
            for (int row = flat.from(); row < flat.from() + flat.depth(); row++) {
                boolean counter = column == tail && row == flat.face();
                place(floor, column, row,
                        (column + row) % 2 == 0 ? STORES : STORES_MATE,
                        counter ? Affordance.STOW : null);
            }
        }
    }

    /**
     * How many boards the room stands a watch at: one per so much deck, and
     * never more than the inboard flat can carry alongside its plant.
     */
    private int boards(RoomFloor floor, int along, int across, int spares) {
        int wanted = Math.max(1, along * across / DECK_PER_BOARD);
        int room = Math.max(0, spares - CROSS - MIN_MACHINE);
        while (wanted > 1 && stationWidth(floor, wanted) > room) wanted--;
        return wanted;
    }

    private int stationWidth(RoomFloor floor, int boards) {
        return boards * span(floor, BOARD)[0] + span(floor, TERMINAL)[0];
    }

    /**
     * The control station at the near end of the inboard flat: the boards, a
     * terminal beside them, and the racks they read from behind.
     *
     * <p>At the near end because a watchkeeper is stationed where the hatch is —
     * the board is the first thing in a machinery space and the last thing out
     * of it. Inboard because the outboard flat is the heavy plant, and a
     * watch position wedged into the drive is not a watch position.
     */
    private void layStation(RoomFloor floor, Flat flat, int boards) {
        int[] board = span(floor, BOARD);
        int[] terminal = span(floor, TERMINAL);
        int cursor = CROSS;
        for (int index = 0; index < boards; index++) {
            placeSpanning(floor, cursor, flat.leadFrom(board[1]), board[0], board[1],
                    BOARD, Affordance.WATCH);
            cursor += board[0];
        }
        placeSpanning(floor, cursor, flat.leadFrom(terminal[1]), terminal[0], terminal[1],
                TERMINAL, Affordance.READOUT);
        cursor += terminal[0];
        for (int column = CROSS; column < cursor; column++) {
            for (int row = flat.from(); row < flat.from() + flat.depth(); row++) {
                place(floor, column, row, STATION_BACK, null);
            }
        }
    }

    /** One 1x1 fixture, and the work at it where this placement affords any. */
    private void place(RoomFloor floor, int along, int across,
                       String id, Affordance affordance) {
        placeSpanning(floor, along, across, 1, 1, id, affordance);
    }

    /**
     * One fixture covering a canonical rectangle.
     *
     * <p>The rectangle is carried across whole rather than by its origin,
     * because a turn moves which corner the origin is. A piece anchored at a
     * mapped origin and grown by its own footprint lies off the end of every
     * quarter-turned room it is placed in.
     */
    private void placeSpanning(RoomFloor floor, int along, int across,
                               int spanAlong, int spanAcross,
                               String id, Affordance affordance) {
        int[] rect = floor.toLocalRect(along, across, spanAlong, spanAcross);
        if (affordance == null) {
            floor.place(id, rect[0], rect[1]);
        } else {
            floor.place(id, rect[0], rect[1], affordance);
        }
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
