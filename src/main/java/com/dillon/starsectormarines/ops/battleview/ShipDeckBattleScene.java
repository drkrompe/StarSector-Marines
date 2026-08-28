package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.JobSite;
import com.dillon.starsectormarines.battle.ambient.Shift;
import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Battle-renderer host for a generated ship deck.
 *
 * <p>A generated deck is already a {@link MapResult} — grid, topology, doodads,
 * buildings, tactical map — which is to say it is already a battle map rather
 * than a preview artifact. So looking at one is not a separate problem from
 * fighting on one: both are {@link BattleRenderer} over a {@link BattleSimulation},
 * and they differ only in the camera, the layer set, and which drain collects
 * the frame. Authoring evidence, an in-game deck view, and a boarding action all
 * arrive here.
 *
 * <p>Rooms are addressable. A deck is one scene, and the screens that look at
 * parts of the ship — the Mech Lab at its vehicle bay, a berthing screen at its
 * barracks — are cameras framing a compartment of it rather than separate rooms
 * built beside it. That is why the compartment graph is carried here and not
 * left behind with the generator: without it a host can draw the deck but cannot
 * say which part of it is the room it is a screen for.
 *
 * <p><b>The whole deck runs.</b> Not the compartment somebody is looking at —
 * the ship. A screen is a camera, and a camera does not decide what exists: the
 * watch in the bay keeps working while the player is reading the berthing
 * screen, and walks out of frame and back into it. Simulating only the framed
 * room would be both more code and less ship, because the alternative to one
 * deck is one private grid per screen kept in step with the others by hand,
 * which is what this replaces.
 *
 * <p>That is also why a deck-hosted screen advances rather than seeks.
 * {@link com.dillon.starsectormarines.battle.ambient.AmbientTaskService#seek}
 * is a presentation teleport which bypasses collision on purpose; on a
 * hand-authored room its straight lines are clear by construction, and on a
 * generated deck they cross bulkheads. A deck that ticks needs no such licence.
 *
 * <p>This owns no HUD, input, or audio. It is a deck, a clock, and a camera
 * over it.
 *
 * <p><b>Pre-condition:</b> the tile catalogs must be installed before
 * construction — the sim bakes overlay cover from {@code TileRegistry} as it is
 * built. In game that happens at application load; a tool must install them (or
 * construct its headless drain, which does) first.
 */
public final class ShipDeckBattleScene implements AutoCloseable {

    /**
     * What an authoring or hosted deck view draws.
     *
     * <p>Roofs and fog are deliberately absent. Both exist to hide interior
     * space from a player who has not earned sight of it, and a deck is
     * interior everywhere — drawn here they would black out the whole map to
     * conceal it from a player who is not present. A view that looks
     * <em>into</em> the deck opts out of concealment; a boarding action, which
     * has a player, would ask for the full set.
     *
     * <p>Decals are absent for a duller reason: the decal pass owns its own GL
     * and has no drain outside a live context. Nothing is lost — a deck that has
     * not been fought over has no bullet holes in it — but a boarding action
     * drawn to an image would notice, so this is a real bound and not a taste.
     */
    public static final EnumSet<RenderLayer> DECK_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.VEHICLES,
            RenderLayer.DOODADS, RenderLayer.UNITS);

    /**
     * Most a single {@link #advanceTo} will actually run, however far the clock
     * has moved. A screen reopened after an hour catches up by a bounded amount
     * and then simply picks the ship up where the rotation has got to.
     */
    private static final float MAX_CATCH_UP_SECONDS = 30f;

    private final BattleRenderer renderer;
    private final BattleSimulation simulation;
    private final List<Gantry> gantries;
    private final List<FixtureTask> fixtureTasks;
    private final boolean[] occupiedBerths;
    private final DeckGraph rooms;
    /**
     * Range targets already spawned, by the cell of butts they stand in. One
     * per set of butts rather than one per shooter: a lane is shot at by
     * whoever has claimed its firing point, and there is only ever one of them.
     */
    private final Map<Long, Long> butts = new HashMap<>();
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();
    /** How far the deck has been run; see {@link #advanceTo}. */
    private float simulatedSeconds;

    /** Scene model only; a tooling drain brings its own renderer and assets. */
    public ShipDeckBattleScene(MapResult deck, long seed) {
        this(deck, null, seed, null);
    }

    public ShipDeckBattleScene(MapResult deck, long seed, BattleSprites sprites) {
        this(deck, null, seed, sprites);
    }

    /**
     * @param rooms the deck's compartment graph, or {@code null} for a scene
     *              nothing will address rooms on
     */
    public ShipDeckBattleScene(MapResult deck, DeckGraph rooms, long seed,
                               BattleSprites sprites) {
        if (deck == null) throw new IllegalArgumentException("a generated deck is required");
        this.rooms = rooms;
        gantries = deck.gantries;
        fixtureTasks = deck.fixtureTasks;
        occupiedBerths = new boolean[gantries.size()];
        simulation = BattleSetup.buildMap(deck, Collections.emptyList(),
                Collections.emptyList(), seed).sim();
        // A deck is not a mission. Left alone, the simulation installs its
        // backstop eliminate-each-other objectives, a deck carrying nobody but
        // its own crew wins the moment it is built, and every advance returns
        // without ticking - so the ship stops dead the first time anybody asks
        // it to run. A boarding action hosted here registers its own objectives
        // and turns this back on.
        simulation.setMissionCompletionEnabled(false);
        simulation.getFogOfWar().tick(0, simulation.getRoster());
        if (sprites == null) {
            renderer = null;
        } else {
            renderer = new BattleRenderer(sprites);
            renderer.buildTileBatches();
        }
    }

    public BattleSimulation simulation() {
        return simulation;
    }

    /** The berths this deck authored, in generation order. */
    public List<Gantry> gantries() {
        return gantries;
    }

    /** The jobs this deck's fixtures afford, in generation order. */
    public List<FixtureTask> fixtureTasks() {
        return fixtureTasks;
    }

    /**
     * The compartment this deck means by a purpose.
     *
     * @throws IllegalStateException if the scene was built without a room graph
     * @throws IllegalArgumentException if the deck placed no such room
     */
    public DeckGraph.Compartment room(RoomPurpose purpose) {
        if (rooms == null) {
            throw new IllegalStateException("this deck scene carries no room graph");
        }
        DeckGraph.Compartment found = rooms.largest(purpose);
        if (found == null) {
            throw new IllegalArgumentException("this deck has no " + purpose);
        }
        return found;
    }

    /**
     * The berths standing inside one compartment, in generation order.
     *
     * <p>A screen framed on a room asks about that room's machines, and a deck
     * may carry berths in more than one place. Filtering by the compartment's
     * own floor rather than by index keeps the two facts — which berths exist
     * and which room they are in — from having to be kept in step by hand.
     */
    public List<Gantry> berthsIn(DeckGraph.Compartment compartment) {
        if (compartment == null) throw new IllegalArgumentException("a compartment is required");
        List<Gantry> found = new ArrayList<>();
        for (Gantry gantry : gantries) {
            if (compartment.contains(gantry.centerX, gantry.centerY)) found.add(gantry);
        }
        return List.copyOf(found);
    }

    /**
     * Stand a lance in the deck's berths, in order, and return the machine in
     * each, aligned with {@link #gantries()}.
     *
     * <p>Occupancy is the host's call, not the map's, which is why this is a
     * separate step rather than something the constructor does. On the home
     * deck the lance is the player's own and this vehicle bay <em>is</em> their
     * lab — the machines shown are the machines they own, so a bay with one
     * mech in it and seven berths empty is the honest picture of a company just
     * starting out, not a rendering gap.
     *
     * <p>Machines are units, not scenery. They arrive from a roster with their
     * real variant and loadout, so what stands in the bay is the same entity
     * that would walk out of it.
     *
     * <p>The ids come back because a screen framed on a berth needs the machine
     * standing in it — selecting a gantry and selecting the mech being worked on
     * are the same act, and rediscovering that by searching the roster for
     * whatever is nearest the berth would be inventing a link that is known here.
     */
    public long[] occupyGantries(List<MechVariant> lance) {
        if (lance == null || lance.isEmpty()) return new long[0];
        int berthed = Math.min(lance.size(), gantries.size());
        long[] machines = new long[berthed];
        for (int index = 0; index < berthed; index++) {
            MechVariant variant = lance.get(index);
            if (variant == null) continue;
            Gantry gantry = gantries.get(index);
            long mech = simulation.spawn(new EntitySpec(
                    "berthed mech " + (index + 1), Faction.MARINE, UnitType.HEAVY_MECH,
                    gantry.centerX, gantry.centerY).mechVariant(variant));
            simulation.world().attachMechLoadout(mech,
                    variant.createLoadout(variant.defaultRole));
            // A berth records the way out, and a machine parked in one faces it.
            FacingSystem.faceStanding(simulation.getEntityWorld(),
                    simulation.getBattleComponents(), mech, gantry.facing.degrees());
            machines[index] = mech;
            occupiedBerths[index] = true;
        }
        simulation.getFogOfWar().tick(0, simulation.getRoster());
        return machines;
    }

    /**
     * Put a watch of one role to work in a compartment, cycling its jobs.
     *
     * <p>Staffing follows berthing rather than preceding it, because half the
     * work in a vehicle bay is work on a machine and there is none to do in an
     * empty one. Call {@link #occupyGantries} first and a technician has
     * something to weld; call this on an empty bay and they fetch parts and read
     * terminals, which is what a technician in an empty bay would in fact be
     * doing.
     *
     * <p>The count is capped by what the room can actually sustain — see
     * {@link com.dillon.starsectormarines.battle.ambient.Shift#capacity} — so a
     * bay is never given more people than it has jobs to hand out. Nobody is
     * spawned to stand in a queue.
     *
     * @return the actors put to work, which may be fewer than asked for
     */
    public long[] staff(DeckGraph.Compartment compartment, CrewRole role, int watch) {
        if (compartment == null) throw new IllegalArgumentException("a compartment is required");
        if (role == null) throw new IllegalArgumentException("a role is required");
        if (watch <= 0) return new long[0];

        // Hostiles only. A technician works on armed machines by definition, and
        // yielding to any combatant means yielding to the mech they are welding
        // - so the crew of a home deck would flee their own bay and stand around
        // the edges of it forever.
        Shift watchBill = watchBill(compartment, role);
        int hands = Math.min(watch, watchBill.capacity());
        List<Long> hired = new ArrayList<>(hands);
        for (int index = 0; index < hands; index++) {
            AmbientTaskRoute shift = watchBill.member(index);
            if (shift == null) break;
            AmbientTaskRoute.Stop start = AmbientTaskService.standingPlace(shift, 0f);
            long hand = simulation.spawn(new EntitySpec(shift.id(), Faction.MARINE,
                    role.unit(),
                    (int) Math.floor(start.worldX()), (int) Math.floor(start.worldY())));
            long butt = liveFireTarget(shift);
            if (butt != 0L) simulation.ambientTasks().assignLiveFire(hand, shift, butt);
            else simulation.ambientTasks().assign(hand, shift);
            hired.add(hand);
        }
        simulation.ambientTasks().settle();
        simulation.getFogOfWar().tick(0, simulation.getRoster());
        long[] actors = new long[hired.size()];
        for (int index = 0; index < actors.length; index++) actors[index] = hired.get(index);
        return actors;
    }

    /**
     * Crew the ship: every compartment that has work for a role gets a watch of
     * it, whether or not anybody is looking at that compartment.
     *
     * <p>A deck is manned or it is not. Staffing only the room a screen frames
     * would make the ship's population a fact about the camera — walk from the
     * bay to the berthing and the technicians you left behind would stop
     * existing, and the marines you arrive to find would have been conjured on
     * the way. It also gets the traffic wrong in the one place it shows: the
     * passage between two compartments is busy because both ends of it are
     * working, and a deck that mans one room has an empty corridor.
     *
     * <p>Posting is per compartment and per role because that is what a shift
     * is posted to. What each watch then <em>reaches</em> is the shift's
     * business — a barracks watch walks to the mess and the range on its own,
     * and manning the mess does not mean stationing anybody there.
     *
     * @param watch most of each role to post to any one compartment; each
     *     posting is still capped by what that compartment can keep busy
     * @return every hand now aboard
     */
    /**
     * Crew the ship to her complement: every posting filled to what it can
     * actually sustain.
     *
     * <p>The honest default. A cap on the watch is a throttle on how much ship
     * exists, and there is nothing to throttle for — the compartments already
     * bound themselves by the work they hold, so a barracks takes the marines
     * its racks can sleep and a bay takes the technicians its jobs can occupy.
     * Asking for fewer than that produces a half-empty ship for no reason
     * anybody could see in the fiction.
     */
    public long[] manDeck() {
        return manDeck(Integer.MAX_VALUE);
    }

    public long[] manDeck(int watch) {
        if (rooms == null) {
            throw new IllegalStateException("this deck scene carries no room graph");
        }
        if (watch <= 0) return new long[0];
        List<Long> aboard = new ArrayList<>();
        for (DeckGraph.Compartment room : rooms.compartments()) {
            for (CrewRole role : CrewRole.values()) {
                for (long hand : staff(room, role, watch)) aboard.add(hand);
            }
        }
        long[] crew = new long[aboard.size()];
        for (int index = 0; index < crew.length; index++) crew[index] = aboard.get(index);
        return crew;
    }

    /**
     * The shift a role posted to this compartment would work, and the jobs it
     * reaches across the rest of the deck.
     *
     * <p>Publishing happens here rather than at spawn, because the claim groups
     * have to exist before anybody is given a route that names them. Reading the
     * bill without hiring anybody is what lets a screen say how many hands a
     * room can keep busy.
     *
     * <p>All this adds to {@link Shift#postedAt} is the candidate set, which is
     * the one part of it that <em>is</em> shipboard: the places a deck's watch
     * can reach are its own compartments. A scene built without a room graph
     * offers none, so a shift posted to one room stays in it.
     */
    public Shift watchBill(DeckGraph.Compartment compartment, CrewRole role) {
        if (compartment == null) throw new IllegalArgumentException("a compartment is required");
        if (role == null) throw new IllegalArgumentException("a role is required");
        Shift bill = Shift.postedAt(role, compartment,
                rooms == null ? null : rooms.compartments(),
                fixtureTasks, occupiedBerths, AmbientThreatPolicy.HOSTILE_COMBATANT);
        for (JobSite site : bill.sites()) {
            JobBoard.publish(simulation.taskPoints(), fixtureTasks, site, occupiedBerths);
        }
        return bill;
    }

    /**
     * The target this shift shoots at, or zero where it does no shooting.
     *
     * <p>A practice stop already knows what it is aiming at: the fitting bound
     * the firing point to the butts it faces, so the stop's focus <em>is</em> the
     * target cell. Matching on that rather than on the actor's index is what
     * keeps a shooter firing down their own lane instead of across a neighbour's.
     *
     * <p>The target belongs to the side that owns the deck, and is spawned once
     * per set of butts rather than once per shooter. A range target is the
     * ship's own equipment, so making it hostile would have the detail flee the
     * paper it came to shoot at — the threat policy cannot tell a target frame
     * from somebody walking in, and should not have to.
     */
    private long liveFireTarget(AmbientTaskRoute shift) {
        for (AmbientTaskRoute.Stop stop : shift.stops()) {
            if (stop.activity() != AmbientActivity.PRACTICING_EQUIPMENT) continue;
            int cellX = (int) Math.floor(stop.focusX());
            int cellY = (int) Math.floor(stop.focusY());
            long key = ((long) cellX << 32) ^ (cellY & 0xffffffffL);
            Long standing = butts.get(key);
            if (standing != null) return standing;
            long target = simulation.spawn(new EntitySpec(
                    "butts " + cellX + "," + cellY, Faction.MARINE,
                    UnitType.RANGE_TARGET, cellX, cellY).role(UnitRole.STRUCTURE));
            butts.put(key, target);
            return target;
        }
        return 0L;
    }

    /**
     * Run the deck forward to an authored time.
     *
     * <p>Monotonic and idempotent, so a host may call it once a frame and then
     * take several render passes off one settled state — a backdrop pass and an
     * actor pass have to agree about where everybody is standing.
     *
     * <p>A screen opened an hour into a voyage does not replay the hour. The
     * catch-up is bounded and the clock then jumps: ambient work is a rotation
     * with no history to lose, so the ship a player walks in on is simply the
     * ship a little further round its loop.
     *
     * @param elapsedSeconds time since this deck came into service; going
     *     backwards is ignored rather than rewound, since a deck has no
     *     recorded past to seek within
     */
    public void advanceTo(float elapsedSeconds) {
        if (!Float.isFinite(elapsedSeconds)) {
            throw new IllegalArgumentException("deck time must be finite");
        }
        float target = Math.max(0f, elapsedSeconds);
        float ahead = target - simulatedSeconds;
        if (ahead <= 0f) return;
        simulation.advance(Math.min(ahead, MAX_CATCH_UP_SECONDS));
        simulatedSeconds = target;
    }

    /** How far this deck has been run. */
    public float simulatedSeconds() {
        return simulatedSeconds;
    }

    public BattleSceneHostPass pass(DeckView view) {
        return pass(view, DECK_LAYERS);
    }

    public BattleSceneHostPass pass(DeckView view, EnumSet<RenderLayer> layers) {
        if (view == null) throw new IllegalArgumentException("a camera framing is required");
        EnumSet<RenderLayer> selected = EnumSet.copyOf(layers);
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                return prepareFrame(viewport, view, alphaMult, selected);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                if (renderer == null) {
                    throw new IllegalStateException("This deck scene has no live renderer");
                }
                BattleSceneFrame frame = prepare(viewport, alphaMult);
                renderer.renderWorld(frame.context(), frame.layers());
            }
        };
    }

    private BattleSceneFrame prepareFrame(CanvasHostViewport viewport, DeckView view,
                                          float alphaMult, EnumSet<RenderLayer> layers) {
        if (viewport.width() <= 0f || viewport.height() <= 0f) {
            throw new IllegalArgumentException("a deck scene requires a visible viewport");
        }
        BattleCamera camera = new BattleCamera(
                simulation.getGrid().getWidth(), simulation.getGrid().getHeight());
        camera.setViewport(viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height(), view.cellPx());
        camera.centerOn(view.centerCellX(), view.centerCellY());
        RenderContext context = new RenderContext(simulation, camera, null,
                alphaMult, 0f, false, highlights, selection,
                BattleRenderHostProfile.EMBEDDED_SCENE);
        return new BattleSceneFrame(context, layers);
    }

    @Override
    public void close() {
        simulation.close();
    }

    /**
     * Where the camera looks and how much of the image one cell gets.
     *
     * <p>Pixels-per-cell is stated rather than derived, because the whole point
     * of an authoring view is to see the art at the size it was drawn: a deck
     * squeezed to fit an arbitrary image is a deck resampled, which is how a
     * fill defect and a scaling defect end up looking the same.
     */
    public record DeckView(float centerCellX, float centerCellY, float cellPx) {

        public DeckView {
            if (!(cellPx > 0f)) {
                throw new IllegalArgumentException("cell size must be positive");
            }
        }

        /** Frame a cell rect at a chosen scale. The image is then {@code cells * cellPx}. */
        public static DeckView over(int left, int top, int width, int height, float cellPx) {
            return new DeckView(left + width * 0.5f, top + height * 0.5f, cellPx);
        }

        /**
         * Frame a whole compartment, with a margin of the hull around it.
         *
         * <p>The surround is not decoration. A room drawn to its own bounds has
         * its doors clipped off at the frame edge, so a doorway and a gap in the
         * bulkhead look identical — and which one it is happens to be the thing
         * a room view is most often opened to check.
         */
        public static DeckView over(DeckGraph.Compartment room, int surroundCells,
                                    float cellPx) {
            if (room == null) throw new IllegalArgumentException("a compartment is required");
            int margin = Math.max(0, surroundCells);
            return over(room.left() - margin, room.top() - margin,
                    room.width() + margin * 2, room.depth() + margin * 2, cellPx);
        }

        /** Frame one berth and the working space around it. */
        public static DeckView on(Gantry berth, int surroundCells, float cellPx) {
            if (berth == null) throw new IllegalArgumentException("a berth is required");
            int margin = Math.max(0, surroundCells);
            return over(berth.left() - margin, berth.bottom() - margin,
                    berth.right() - berth.left() + 1 + margin * 2,
                    berth.top() - berth.bottom() + 1 + margin * 2, cellPx);
        }

        /** Frame a cell rect into a fixed image, taking whatever scale that implies. */
        public static DeckView fitting(int left, int top, int width, int height,
                                       float imageWidth, float imageHeight) {
            float cellPx = Math.min(imageWidth / Math.max(1, width),
                    imageHeight / Math.max(1, height));
            return over(left, top, width, height, cellPx);
        }
    }
}
