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
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
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

    /**
     * Built where the game's assets are, which is not necessarily where the
     * scene was. @see #attachRenderer
     */
    private BattleRenderer renderer;
    private final BattleSimulation simulation;
    private final List<Gantry> gantries;
    private final List<FixtureTask> fixtureTasks;
    private final boolean[] occupiedBerths;
    private final long[] berthedMechs;
    /**
     * Which of the deck's berths hold machines, by index into {@link #gantries}.
     *
     * <p>The deck's berths are one list because they are one kind of thing —
     * cleared ground with a heading and servicing beside it — and what stands in
     * one is the host's business. A lance is stood in the machine berths only:
     * before this the lance was walked against the flat list, so the first mech
     * of a company whose deck happened to lay its boat bay first was parked in a
     * ship's boat, which is a legal spawn and looks like nothing until somebody
     * counts the boats.
     *
     * <p>Indices rather than a filtered list, because the berth index is what
     * {@code FixtureTask.berth()} names and what {@link #occupiedBerths} is
     * addressed by: re-numbering the machine berths from zero would publish
     * servicing against the wrong ones.
     */
    private final int[] machineBerths;
    /**
     * Watch bills already drawn up, by compartment and role.
     *
     * <p>Drawing one up reads every compartment on the deck and every fixture
     * task on it, and publishes the jobs it finds to the board. Manning a ship
     * hires one hand at a time and goes round every posting until the bunks run
     * out, so a capital drew each of its bills several hundred times over — the
     * larger half of the half-minute it took to open her.
     *
     * <p>Cleared when a berth is filled, because what a bay has to offer
     * depends on whether there is a machine standing in it.
     */
    private final Map<String, Shift> watchBills = new HashMap<>();
    private final DeckGraph rooms;
    /**
     * Range targets already spawned, by the cell of butts they stand in. One
     * per set of butts rather than one per shooter: a lane is shot at by
     * whoever has claimed its firing point, and there is only ever one of them.
     */
    private final Map<Long, Long> butts = new HashMap<>();
    /**
     * Billets already filled, keyed by compartment and role.
     *
     * <p>A posting is filled once. Without this, mustering the company's own
     * marines into their berthing and then crewing the ship would put a second
     * marine watch in the same racks — anonymous hands sleeping alongside the
     * named ones, in a room whose whole population is on the roster.
     */
    private final Map<String, Integer> billets = new HashMap<>();
    /**
     * Hands taken on so far, by the kind of compartment they sleep in.
     *
     * <p>Counted across the whole ship rather than per posting, because a bunk
     * is spent wherever its occupant works. Mustering the company's marines by
     * name spends barracks racks through the same counter, so crewing the ship
     * afterwards cannot berth somebody in a rack the roster is already in.
     */
    private final Map<RoomPurpose, Integer> hired = new EnumMap<>(RoomPurpose.class);
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
        berthedMechs = new long[gantries.size()];
        machineBerths = machineBerthsIn(gantries);
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
        if (sprites != null) attachRenderer(sprites);
    }

    /**
     * Give this scene the means to draw itself.
     *
     * <p>Separable from building the scene because the two have different
     * homes. A scene is arithmetic and can be assembled and crewed anywhere; a
     * renderer reads sheets the game loaded and belongs on the thread that owns
     * them. Splitting them is what lets a capital's ship be got ready without
     * the frame waiting for her.
     */
    public void attachRenderer(BattleSprites sprites) {
        if (sprites == null || renderer != null) return;
        renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();
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
     * Stand a lance in the deck's <em>machine</em> berths, in order, and return
     * the machine in each, aligned with the lance.
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
        int berthed = Math.min(lance.size(), machineBerths.length);
        long[] machines = new long[berthed];
        for (int seat = 0; seat < berthed; seat++) {
            MechVariant variant = lance.get(seat);
            if (variant == null) continue;
            int index = machineBerths[seat];
            Gantry gantry = gantries.get(index);
            long mech = simulation.spawn(new EntitySpec(
                    "berthed mech " + (seat + 1), Faction.MARINE, UnitType.HEAVY_MECH,
                    gantry.centerX, gantry.centerY).mechVariant(variant));
            simulation.world().attachMechLoadout(mech,
                    variant.createLoadout(variant.defaultRole));
            // A berth records the way out, and a machine parked in one faces it.
            FacingSystem.faceStanding(simulation.getEntityWorld(),
                    simulation.getBattleComponents(), mech, gantry.facing.degrees());
            machines[seat] = mech;
            berthedMechs[index] = mech;
            occupiedBerths[index] = true;
        }
        watchBills.clear();
        simulation.getFogOfWar().tick(0, simulation.getRoster());
        return machines;
    }

    /**
     * Reprojects campaign-authoritative fitting into the live home-deck scene.
     * Existing machines keep their entity identity; a newly fabricated chassis
     * is stood in the next previously vacant berth.
     */
    public void syncGantries(List<MechDeploymentSpec> deployments) {
        if (deployments == null) return;
        int count = Math.min(deployments.size(), machineBerths.length);
        for (int seat = 0; seat < count; seat++) {
            MechDeploymentSpec deployment = deployments.get(seat);
            if (deployment == null) continue;
            int index = machineBerths[seat];
            long mech = berthedMechs[index];
            if (mech == 0L) {
                Gantry gantry = gantries.get(index);
                mech = simulation.spawn(new EntitySpec(
                        "berthed mech " + (seat + 1), Faction.MARINE, UnitType.HEAVY_MECH,
                        gantry.centerX, gantry.centerY).mechVariant(deployment.variant()));
                FacingSystem.faceStanding(simulation.getEntityWorld(),
                        simulation.getBattleComponents(), mech, gantry.facing.degrees());
                berthedMechs[index] = mech;
                occupiedBerths[index] = true;
            }
            MechLoadoutComponent loadout = new MechLoadoutComponent(deployment.variant(),
                    deployment.arms(), deployment.leftShoulder(), deployment.rightShoulder(),
                    deployment.role());
            loadout.installMissileReplenisher(deployment.missileReplenisher());
            simulation.world().attachMechLoadout(mech, loadout);
        }
    }

    /** The indices of every berth on the deck that holds a machine. */
    private static int[] machineBerthsIn(List<Gantry> berths) {
        int[] found = new int[berths.size()];
        int count = 0;
        for (int index = 0; index < berths.size(); index++) {
            if (berths.get(index).holds == Gantry.Holds.MACHINE) found[count++] = index;
        }
        return Arrays.copyOf(found, count);
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
        // Hostiles only. A technician works on armed machines by definition, and
        // yielding to any combatant means yielding to the mech they are welding
        // - so the crew of a home deck would flee their own bay and stand around
        // the edges of it forever.
        long[] hands = hire(compartment, role, watch,
                (billet, shift, cellX, cellY) -> new EntitySpec(
                        shift.id(), Faction.MARINE, role.unit(), cellX, cellY));
        readyHands();
        return hands;
    }

    /**
     * Put the company marines in their berthing, by name.
     *
     * <p>The difference between this and {@link #staff} is who turns up. A ship
     * needs a marine watch in her barracks either way; on the company ship those
     * marines are a roster the player has been reading all game, with their own
     * weapons and armour issued to them. Spawning anonymous hands beside a list
     * of names would make the berthing screen a picture of somebody other than
     * the company.
     *
     * <p>They work the ordinary berthing shift - turning in, squaring kit away,
     * eating, keeping their shooting in - so a marine on the roster is a marine
     * the player can watch walk to the mess. Nothing about being named changes
     * what they do aboard.
     *
     * <p>Berths are finite and the roster is not. A company with more marines
     * than the hull has racks musters as many as she can billet and no more,
     * which is a fact about the ship the player should be able to see rather
     * than a rendering limit - the returned array is zero for anyone left
     * without a billet, in roster order.
     *
     * <p>The posting is then closed, even for a company too small to fill it or
     * for no company at all. However few came aboard, these are the ship's
     * marines; a rack the roster cannot fill stays empty rather than acquiring
     * somebody who is on no muster roll, so {@link #manDeck} must find nothing
     * left to hire here.
     *
     * @return the entity mustered for each soldier, aligned with the list
     */
    public long[] muster(DeckGraph.Compartment berthing, List<MarineSoldier> company) {
        if (berthing == null) throw new IllegalArgumentException("a compartment is required");
        List<MarineSoldier> roll = company == null ? List.of() : company;
        long[] mustered = hire(berthing, CrewRole.MARINE, roll.size(),
                (billet, shift, cellX, cellY) -> specFor(roll.get(billet), cellX, cellY));
        readyHands();
        billets.put(billet(berthing, CrewRole.MARINE), Integer.MAX_VALUE);
        long[] bySoldier = new long[roll.size()];
        System.arraycopy(mustered, 0, bySoldier, 0, mustered.length);
        return bySoldier;
    }

    /**
     * Fill up to {@code watch} of a compartment's billets for a role, taking the
     * next free ones.
     *
     * <p>Indexing from the billets already filled rather than from zero is what
     * keeps two postings to one room apart: a shift member's index is their
     * phase and their starting job, so hiring twice from zero would produce
     * pairs of people walking the same loop in step.
     */
    private long[] hire(DeckGraph.Compartment compartment, CrewRole role,
                        int watch, Hand hand) {
        if (watch <= 0) return new long[0];
        String billet = billet(compartment, role);
        int filled = billets.getOrDefault(billet, 0);
        Shift watchBill = watchBill(compartment, role);
        int hands = Math.max(0, Math.min(watch, watchBill.capacity() - filled));
        List<Long> hired = new ArrayList<>(hands);
        for (int index = 0; index < hands; index++) {
            AmbientTaskRoute shift = watchBill.member(filled + index);
            if (shift == null) break;
            AmbientTaskRoute.Stop start = AmbientTaskService.standingPlace(shift, 0f);
            long actor = simulation.spawn(hand.reportingFor(index, shift,
                    (int) Math.floor(start.worldX()), (int) Math.floor(start.worldY())));
            long butt = liveFireTarget(shift);
            if (butt != 0L) simulation.ambientTasks().assignLiveFire(actor, shift, butt);
            else simulation.ambientTasks().assign(actor, shift);
            hired.add(actor);
        }
        billets.put(billet, filled + hired.size());
        this.hired.merge(role.quarters(), hired.size(), Integer::sum);
        long[] actors = new long[hired.size()];
        for (int index = 0; index < actors.length; index++) actors[index] = hired.get(index);
        return actors;
    }

    /**
     * Put everybody aboard at their first stop and let them see the ship.
     *
     * <p>Both passes are over the whole complement, so this is done once a
     * crewing is finished rather than once per hand taken on. Manning a deck
     * hires one at a time and goes round the postings until the bunks run out —
     * settling the entire roster after each of those was quadratic in the
     * complement, and cost half a minute on a capital: three quarters of what
     * it took to open the ship at all, and none of it work.
     */
    private void readyHands() {
        simulation.ambientTasks().settle();
        simulation.getFogOfWar().tick(0, simulation.getRoster());
    }

    private static String billet(DeckGraph.Compartment compartment, CrewRole role) {
        return compartment.id() + "/" + role.name();
    }

    /** Who fills one billet of a shift, standing at the cell it starts on. */
    @FunctionalInterface
    private interface Hand {
        EntitySpec reportingFor(int billet, AmbientTaskRoute shift, int cellX, int cellY);
    }

    /**
     * A soldier as they would come aboard: their own name, their own kit.
     *
     * <p>Issued armour and weapon rather than a generic marine, because the
     * berthing is where the player reads what their people are carrying - and
     * because a marine at the range should be shooting the weapon the armoury
     * gave them.
     */
    private static EntitySpec specFor(MarineSoldier soldier, int cellX, int cellY) {
        EntitySpec spec = new EntitySpec(soldier.name(), Faction.MARINE,
                UnitType.MARINE, cellX, cellY);
        MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                soldier.primaryDef(), soldier.primaryGrade(),
                SquadExperienceStandard.profileFor(soldier),
                soldier.specialEquipmentDef(),
                soldier.id(), soldier.armorDef().appearanceFamily(),
                soldier.armorDef().armorCapacity(), soldier.armorDef().armorRating(),
                soldier.armorDef().moveSpeedMult(),
                soldier.armorDef().incomingAccuracyMult(), null).seedInto(spec);
        return spec;
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
     * is posted to, and only where that role is actually based — see
     * {@link Shift#basedAt}. What each watch then <em>reaches</em> is the
     * shift's business: a barracks watch walks to the mess and the range on its
     * own, and manning the mess does not mean stationing anybody there. Crewing
     * every room that merely offers a role something to do is how a ship ends
     * up with a watch of marines quartered in the galley.
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
        List<Posting> postings = new ArrayList<>();
        for (DeckGraph.Compartment room : rooms.compartments()) {
            for (CrewRole role : CrewRole.values()) {
                if (!Shift.basedAt(role, room, fixtureTasks, occupiedBerths)) continue;
                postings.add(new Posting(room, role));
            }
        }
        // One at a time, round the postings, until either the work runs out or
        // the bunks do. Filling each posting to its own capacity in turn would
        // let the first rooms in the list take the whole complement, and the
        // ship would come out fully crewed forward and deserted aft.
        List<Long> aboard = new ArrayList<>();
        Map<RoomPurpose, Integer> berths = new EnumMap<>(RoomPurpose.class);
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (Posting posting : postings) {
                int budget = berths.computeIfAbsent(posting.role().quarters(),
                        this::bunksIn) - hired.getOrDefault(posting.role().quarters(), 0);
                if (budget <= 0) continue;
                long[] hand = hire(posting.room(), posting.role(), 1,
                        (billet, shift, cellX, cellY) -> new EntitySpec(shift.id(),
                                Faction.MARINE, posting.role().unit(), cellX, cellY));
                if (hand.length == 0) continue;
                aboard.add(hand[0]);
                progressed = true;
            }
        }
        readyHands();
        long[] crew = new long[aboard.size()];
        for (int index = 0; index < crew.length; index++) crew[index] = aboard.get(index);
        return crew;
    }

    /** One role's station in one compartment. */
    private record Posting(DeckGraph.Compartment room, CrewRole role) { }

    /**
     * Racks in the compartments of this kind, which is how many people the ship
     * can carry of whoever sleeps in them.
     *
     * <p>The honest bound on a complement, and the only one available: what a
     * room can keep <em>busy</em> is a fact about its fixtures and says nothing
     * about whether the ship can carry the people to do it. Read off the fill
     * rather than off the hull's stated crew, because the racks are the thing
     * the player can walk up and count.
     *
     * <p>Without it a deck crews itself out of its own furniture. A spares
     * pocket with three stowage points is three more storekeepers, and a hull
     * that fills every leftover corner with such pockets carried two hundred and
     * seventy-six engineers on ten bunks - a ship's company several times her
     * own lift, none of whom had anywhere to sleep.
     */
    private int bunksIn(RoomPurpose quarters) {
        int bunks = 0;
        for (FixtureTask task : fixtureTasks) {
            if (task.affordance() != Affordance.REST || !task.inService()) continue;
            for (DeckGraph.Compartment room : rooms.compartments()) {
                if (room.purpose() != quarters) continue;
                if (room.contains(task.cellX(), task.cellY())) {
                    bunks++;
                    break;
                }
            }
        }
        return bunks;
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
        return watchBills.computeIfAbsent(billet(compartment, role),
                key -> drawUp(compartment, role));
    }

    /** @see #watchBills */
    private Shift drawUp(DeckGraph.Compartment compartment, CrewRole role) {
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

    /**
     * Draw the ship as a room screen frames her: a compartment fitted to the
     * viewport, then zoomed and panned within it.
     *
     * <p>A different framing from {@link DeckView} and deliberately so. Authoring
     * evidence states a cell size because the point is to see the art at the
     * size it was drawn; an interactive room view states a <em>room</em>, because
     * the point is that the compartment fills the panel whatever shape the panel
     * is and whatever size the ship is. Fitting the whole deck instead would put
     * a three-hundred-frame hull in a sidebar and call the resulting four-pixel
     * cells a vehicle bay.
     *
     * <p>What is drawn is still the whole ship. Only the camera is on the room:
     * crew walk in from the passage and out to the mess, because they are going
     * to the mess.
     */
    public BattleSceneHostPass pass(RoomView view) {
        return pass(view, DECK_LAYERS);
    }

    public BattleSceneHostPass pass(RoomView view, EnumSet<RenderLayer> layers) {
        if (view == null) throw new IllegalArgumentException("a room framing is required");
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

    /**
     * The camera a room view puts on the ship, for a host that needs to project
     * world points onto its own surface — a fitting overlay has to land on the
     * mech it annotates.
     */
    public BattleCamera cameraFor(RoomView view, float screenX, float screenY,
                                  float width, float height) {
        if (view == null) throw new IllegalArgumentException("a room framing is required");
        BattleCamera camera = new BattleCamera(
                simulation.getGrid().getWidth(), simulation.getGrid().getHeight());
        camera.setViewport(screenX, screenY, width, height, view.fittedCellPx(width, height));
        camera.zoomAt(view.zoomNotches(), screenX + width * 0.5f, screenY + height * 0.5f);
        camera.centerOn(view.centerCellX(), view.centerCellY());
        return camera;
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

    private BattleSceneFrame prepareFrame(CanvasHostViewport viewport, RoomView view,
                                          float alphaMult, EnumSet<RenderLayer> layers) {
        if (viewport.width() <= 0f || viewport.height() <= 0f) {
            throw new IllegalArgumentException("a deck scene requires a visible viewport");
        }
        BattleCamera camera = cameraFor(view, viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height());
        RenderContext context = new RenderContext(simulation, camera, null,
                alphaMult, 0f, false, highlights, selection,
                BattleRenderHostProfile.DECK_SCENE);
        return new BattleSceneFrame(context, layers);
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
                BattleRenderHostProfile.DECK_SCENE);
        return new BattleSceneFrame(context, layers);
    }

    @Override
    public void close() {
        simulation.close();
    }

    /**
     * An operations screen's camera on one compartment: what to fit, where to
     * look, and how far in.
     *
     * <p>The fitted extent and the look-at point are separate on purpose. The
     * extent is the room, and it decides the scale — so the panel shows a bay
     * whether the bay is on a cruiser or a capital. The look-at point is
     * wherever the screen's attention is, which is usually a berth rather than
     * the middle of the room, and it moves while the extent does not. Deriving
     * the scale from the look-at point instead would make the room breathe every
     * time the player selected a different machine.
     *
     * @param fitAcross cells the framing fits across; the room plus its surround
     * @param fitDown cells the framing fits down
     * @param zoomNotches wheel notches in from the fitted scale, so a screen can
     *     ease between a wide shot and a close one without knowing pixel sizes
     */
    public record RoomView(int fitLeft, int fitTop, int fitAcross, int fitDown,
                           float centerCellX, float centerCellY, float zoomNotches) {

        public RoomView {
            if (fitAcross <= 0 || fitDown <= 0) {
                throw new IllegalArgumentException("a room framing fits a positive extent");
            }
            if (!Float.isFinite(centerCellX) || !Float.isFinite(centerCellY)
                    || !Float.isFinite(zoomNotches)) {
                throw new IllegalArgumentException("room framing coordinates must be finite");
            }
        }

        /**
         * Frame a compartment with a margin of the hull around it, looking at
         * its middle.
         *
         * <p>The surround is not decoration: a room drawn to its own bounds has
         * its doors clipped off at the frame edge, so a doorway and a gap in the
         * bulkhead look identical — and a room view is most often opened to see
         * who is coming through one.
         */
        public static RoomView of(DeckGraph.Compartment room, int surroundCells) {
            return of(room, surroundCells, 0f);
        }

        public static RoomView of(DeckGraph.Compartment room, int surroundCells,
                                  float zoomNotches) {
            if (room == null) throw new IllegalArgumentException("a compartment is required");
            int margin = Math.max(0, surroundCells);
            return new RoomView(room.left() - margin, room.top() - margin,
                    room.width() + margin * 2, room.depth() + margin * 2,
                    room.left() + room.width() * 0.5f, room.top() + room.depth() * 0.5f,
                    zoomNotches);
        }

        /** The same framing, looking somewhere else in the room and zoomed in. */
        public RoomView lookingAt(float cellX, float cellY, float notches) {
            return new RoomView(fitLeft, fitTop, fitAcross, fitDown, cellX, cellY, notches);
        }

        /** Cell size that fits this framing's extent to a viewport of this shape. */
        public float fittedCellPx(float width, float height) {
            return Math.min(width / fitAcross, height / fitDown);
        }
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
