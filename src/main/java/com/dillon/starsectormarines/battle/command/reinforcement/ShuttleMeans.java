package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Air-drop reinforcement means. Picks a viable LZ near the rally via
 * {@link LandingZoneScorer} (walkable, outside building footprints, with a
 * little clearance), spawns a single-cycle air craft that flies in from
 * the side-appropriate
 * off-map edge, lands, deboards its capacity into a fresh defender squad,
 * and departs. Reuses the existing shuttle state machine in
 * {@code AirSystem} — this class only writes the spawn-time inputs the
 * sim already consumes for marine drops.
 *
 * <p>Selected against {@link ConvoyMeans} and {@link WalkInMeans} on
 * {@link #arrivalSeconds}, not on a registration slot. A rally in a clogged
 * interior with no LZ within {@link #LZ_SCAN_RADIUS} cells is infeasible and
 * yields; a rally the trucks would take a minute to reach is where a sortie
 * wins on merit.
 *
 * <p>Narrative read: shuttle reinforcement is an elite strike team deploying
 * via aircraft. It explicitly selects the requesting faction's elite roster
 * tier, while {@link WalkInMeans} selects bulk infantry.
 */
public final class ShuttleMeans implements ReinforcementMeans {

    private static final Logger LOG = Global.getLogger(ShuttleMeans.class);

    /** Max search radius (Manhattan) from the rally when scoring an LZ. */
    private static final int LZ_SCAN_RADIUS = 8;

    /**
     * Minimum open-neighbour count an LZ must have. A shuttle is an aircraft —
     * it shouldn't set down in a one-cell pinch between buildings. Lenient (a
     * cell touching a wall still qualifies); the load-bearing constraint is the
     * scorer's walkable / no-building viability rule.
     */
    private static final int SHUTTLE_MIN_CLEARANCE = 2;


    /**
     * Default shuttle for SMALL strength. Nimble, 4-capacity — single-squad
     * reinforcement reads as quick-response delivery.
     *
     * <p>Public because it is also what stands on a garrison hardstand: the
     * aircraft a field is based with has to be the aircraft its sorties fly, or
     * an attacker burns one hull and a different one takes off.
     */
    public static final ShuttleType SORTIE_TYPE = ShuttleType.AEROSHUTTLE;
    private static final ShuttleType DEFAULT_TYPE = SORTIE_TYPE;

    private final TraversalAxis axis;
    private final GroundRosterProfile groundRoster;
    private final RiskLevel risk;
    /**
     * Where the defender considers it safe to put a delivery down, or null on a
     * battle with no such authority.
     *
     * <p>The same policy the convoy asks, and asked for the same reason: it is
     * the only thing on the field that knows where the hostile front is. A
     * request's rally is where force is <em>needed</em>, which during a losing
     * fight is exactly where the marines are — land on it and the sortie
     * deboards a squad into whoever just took the position. The policy answers
     * the separate question the reinforcement model already separates out, of
     * where a means may safely arrive.
     */
    private final DeliveryDeploymentPolicy deploymentPolicy;
    /**
     * Sim-seconds a loaded-on-the-ground sortie waits for its squad to march
     * out to the pad before going with whoever arrived.
     *
     * <p>Long enough to cross a ward on foot, short enough that a squad killed
     * on the way does not park an aircraft for the rest of the battle.
     */
    private static final float BOARDING_PATIENCE = 90f;

    /**
     * How much longer a walk across a built-up map is than the straight line
     * it covers. The crew goes round the buildings between the rear edge and
     * the field like anyone else.
     */
    private static final float FOOT_DETOUR = 1.35f;

    /** Hardstands on the garrison's own airfield, in map order. Empty on a map with none. */
    private final List<LandingPad> airfield;
    /** Names the marines this means marches out to its pads. */
    private int nextEmbarkId;

    public ShuttleMeans(TraversalAxis axis) {
        this(axis, null, RiskLevel.LOW);
    }

    public ShuttleMeans(TraversalAxis axis, GroundRosterProfile groundRoster, RiskLevel risk) {
        this(axis, groundRoster, risk, null, List.of());
    }

    public ShuttleMeans(TraversalAxis axis, GroundRosterProfile groundRoster, RiskLevel risk,
                        DeliveryDeploymentPolicy deploymentPolicy, List<LandingPad> landingPads) {
        this.axis = axis;
        this.groundRoster = groundRoster;
        this.risk = risk != null ? risk : RiskLevel.LOW;
        this.deploymentPolicy = deploymentPolicy;
        List<LandingPad> field = new ArrayList<>();
        for (LandingPad pad : landingPads == null ? List.<LandingPad>of() : landingPads) {
            if (pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD) field.add(pad);
        }
        this.airfield = List.copyOf(field);
    }

    @Override
    public boolean canFulfill(BattleView sim, ReinforcementRequest req) {
        if (!req.hasRally()) return false;
        if (req.side != Faction.DEFENDER) return false;
        // Compound-as-supply gate: shuttle drops are sourced from the
        // defender COMMAND_POST — the strategic-control surface that
        // authorises an air-drop in the first place. Lose every command
        // post and the air arm has nothing to dispatch from.
        if (!sim.getCompoundService().hasAliveCompound(
                TacticalNode.Kind.COMMAND_POST, Faction.DEFENDER)) {
            return false;
        }
        // And the field the aircraft actually fly from, on a map that has one.
        // A command post authorises a drop; an airfield is where the lift
        // lives, so taking the field ends air delivery whoever still holds the
        // headquarters. A map with no airfield keeps the command post as its
        // only gate, exactly as before — the field cannot be a requirement on
        // battles that were never given one.
        if (!airfield.isEmpty() && !sim.getCompoundService().hasAliveCompound(
                TacticalNode.Kind.AIRBASE, Faction.DEFENDER)) {
            return false;
        }
        // And an aircraft to fly, on a stand this sortie can lift off. Holding
        // the ground and having something to put in the air are different
        // things, and both have to be true: a field whose aircraft have been
        // burned on their pads supplies nothing however firmly its perimeter is
        // still held.
        //
        // Asked about hardstands specifically, because that is the only kind
        // this means can use. Asking whether anything at all was airworthy said
        // yes on the strength of a fighter parked in a shed, which a transport
        // can neither reach nor lift out of — and the dispatch below then found
        // no stand and conjured an aircraft onto the nearest bare pad instead.
        if (!sim.getAirfieldService().berths().isEmpty()
                && !sim.getAirfieldService().hasAirworthyAirframe(
                        AirfieldService.Kind.HARDSTAND)) {
            return false;
        }
        int[] centre = deliveryCentre(req);
        return new LandingZoneScorer(sim.getGrid(), sim.getTopology())
                .bestNear(centre[0], centre[1], LZ_SCAN_RADIUS, SHUTTLE_MIN_CLEARANCE) != null;
    }

    /**
     * How long a sortie takes to put a squad down: the crew's walk out to the
     * ramp, then the flight.
     *
     * <p>The walk is the half that matters. An aircraft is three or four times
     * a truck's speed and would win every request on flight time alone, which
     * would leave the convoy as dead as the airfield was — but a sortie off an
     * authored field does not leave until somebody has boarded it, and those
     * people start at the side's own rear edge and cross the ground between.
     * Counting that walk is what makes air the answer to a call the trucks
     * cannot reach in time rather than the answer to all of them.
     *
     * <p>A sortie with no field behind it flies in loaded from off-map and
     * owes no walk at all.
     */
    @Override
    public float arrivalSeconds(BattleView sim, ReinforcementRequest req) {
        int[] centre = deliveryCentre(req);
        AirfieldService.Berth berth = sim.getAirfieldService()
                .nearestAirworthy(centre[0] + 0.5f, centre[1] + 0.5f);
        float fromX = berth != null ? berth.centerX : centre[0];
        float fromY = berth != null ? berth.centerY : centre[1];
        float flight = distance(fromX, fromY, centre[0], centre[1])
                / Math.max(0.1f, DEFAULT_TYPE.maxSpeed);
        return crewWalkSeconds(sim, req, berth) + flight;
    }

    /**
     * Sim-seconds the ground crew spends walking to the ramp, or zero for a
     * sortie that arrives already loaded.
     *
     * <p>Measured from the same perimeter cell {@link #embarkOnPad} actually
     * marches them in from, so the estimate and the delivery agree about where
     * the crew comes from.
     */
    private float crewWalkSeconds(BattleView sim, ReinforcementRequest req,
                                  AirfieldService.Berth berth) {
        if (berth == null || airfield.isEmpty()) return 0f;
        int[] from = WalkInMeans.pickPrimaryCell(sim, req, axis);
        if (from == null) return 0f;
        float walk = distance(from[0], from[1], berth.centerX, berth.centerY)
                * FOOT_DETOUR;
        return walk / Math.max(0.1f, UnitType.MARINE.moveSpeed);
    }

    private static float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public ReinforcementDispatchResult dispatch(BattleControl sim,
                                                ReinforcementRequest req) {
        NavigationGrid grid = sim.getGrid();
        DeliveryDeployment deployment = deploymentFor(req);
        int[] centre = { deployment.hintX(), deployment.hintY() };
        int[] lz = new LandingZoneScorer(grid, sim.getTopology())
                .bestNear(centre[0], centre[1], LZ_SCAN_RADIUS, SHUTTLE_MIN_CLEARANCE);
        if (lz == null) {
            LOG.warn("ShuttleMeans: no viable LZ within " + LZ_SCAN_RADIUS
                    + " cells of centre=(" + centre[0] + "," + centre[1] + ")"
                    + " rally=(" + req.rallyX + "," + req.rallyY + ")");
            return ReinforcementDispatchResult.REJECTED;
        }

        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;

        // An aircraft off the field is a specific aircraft standing on a
        // specific pad, not a new one conjured at those coordinates. Taking it
        // from its berth is what makes the field finite: the hull that leaves
        // is the hull that was there, damage and all, and the pad it left is
        // empty until it comes back.
        AirfieldService.Berth berth = sim.getAirfieldService()
                .nearestAirworthy(lzX, lzY);
        if (berth == null && !sim.getAirfieldService().berths().isEmpty()) {
            // A field with nothing to send does not send anything. The
            // feasibility gate should have caught this already; refusing here
            // too is what stops a disagreement between the two from quietly
            // becoming a shuttle nobody owns.
            LOG.warn("ShuttleMeans: no airworthy hardstand at dispatch though the"
                    + " field reported one — declining rather than conjuring a hull");
            return ReinforcementDispatchResult.REJECTED;
        }
        float[] entry = berth != null
                ? sortieFromBerth(berth)
                : entryForSide(req.side, axis, lzX, lzY, grid.getWidth(), grid.getHeight());

        long shuttleId = sim.spawnShuttle(
                DEFAULT_TYPE, req.side,
                lzX, lzY,
                entry[0], entry[1],
                entry[2], entry[3],
                /*pendingDelay*/ 0f);
        ShuttleMission mission = sim.world().mission(shuttleId);
        if (berth != null) {
            sim.world().setHp(shuttleId, sim.getAirfieldService().launch(berth));
            mission.homeBerth = berth;
        }
        // Whoever the delivery policy says owns a delivered squad — the mission
        // commander on a battle that has one. A sortie's passengers are the
        // commander's people once they are on the ground, the same as a
        // convoy's; claiming them for the air arm instead left every squad a
        // shuttle ever dropped outranking the commander that asked for it, and
        // so unable to be moved for the rest of the battle.
        mission.commandClaim = deployment.squadClaim();
        mission.commandOwnsObjective = deployment.commandOwnsObjective();
        mission.totalCycles = 1;
        boolean loadedOnTheGround = !airfield.isEmpty()
                && embarkOnPad(sim, req, deployment, mission);
        // Objective assignment (progressive-reinforcement slice 4): resolve the
        // request's objective to a tactical node now, at dispatch time, so the
        // deboarded squad is assigned the moment it lands rather than only once
        // it physically walks to the position — see ObjectiveNodes.
        mission.assignNode = ObjectiveNodes.resolve(sim.getTacticalMap(), req);
        // An objective with no authored place behind it — a lost zone — still
        // names somewhere to retake, so carry it as a zone rather than dropping
        // the task on the floor. Same fallback the convoy makes.
        if (mission.assignNode == null && req.hasObjective()
                && sim.getZoneGraph() != null) {
            mission.assignZoneId = sim.getZoneGraph().zoneIdAt(
                    req.objectiveX, req.objectiveY);
        }
        // Reinforcement shuttles deboard the faction's elite tier (the
        // narrative of "expensive air-drop = stiffening delivery"). Default
        // player shuttles leave deboardUnitType null and get the bulk
        // infantry slot — see reinforcement-nouns.md.
        GroundRosterProfile effectiveRoster = groundRoster != null
                ? groundRoster : sim.getGroundRoster();
        mission.deboardUnitType = effectiveRoster != null
                ? effectiveRoster.unitType(GroundRosterProfile.ForceTier.ELITE)
                : FactionUnitRoster.forFaction(req.side).elite();
        if (effectiveRoster != null) {
            mission.marineLoadout = InfantryLoadoutRolls.defenderSquad(
                    DEFAULT_TYPE.capacity, effectiveRoster,
                    GroundRosterProfile.ForceTier.ELITE, risk, sim.random());
        } else {
            mission.marineLoadout = InfantryLoadoutRolls.defenderSquad(
                    DEFAULT_TYPE.capacity, mission.deboardUnitType,
                    risk, sim.random());
        }
        LOG.info("ShuttleMeans: dispatched " + DEFAULT_TYPE + " side=" + req.side
                + " lz=(" + lz[0] + "," + lz[1] + ") entry=(" + entry[0] + "," + entry[1] + ")"
                + " from=" + (airfield.isEmpty() ? "offmap"
                        : loadedOnTheGround ? "airfield-embark" : "airfield"));
        return ReinforcementDispatchResult.COMMITTED;
    }

    /**
     * Hold the craft on its pad and march a squad out to board it.
     *
     * <p>This is the difference between an air arm and a spawner. A sortie that
     * arrives already loaded has no cost and no story: the aircraft is a
     * delivery mechanism that happens to be drawn. A sortie that has to be
     * loaded has both — the garrison commits people it can see, they cross open
     * ground to reach the field, and an attacker who is on the airfield, or
     * merely shooting across it, has stopped the lift without touching the
     * aircraft.
     *
     * <p>The squad walks in from the side's own rear edge, the same place the
     * walk-in means brings one on, and is sent to <em>the ramp</em> rather than
     * to the airfield. The distinction is the whole of whether anyone boards.
     * An apron is a wide place — three stands across some twenty-seven cells —
     * and its tactical node anchors at the centre of it, so a crew pointed at
     * the node arrives on the field several cells from the aircraft, outside
     * the reach {@code AirSystem} loads from, and mills there until the sortie
     * times out. Every sortie then left another four standing on the paving.
     *
     * <p>What they are given is a defend-site task on the pad cell, which is
     * how a garrison is told to hold a place: they walk to it, stand in a
     * fire-team footprint that fits inside the boarding reach, break off to
     * shoot at what they can see, and turn toward gunfire they can hear. That
     * is what a ground crew waiting on a lift should look like, and it costs
     * no bespoke behaviour — the sortie contributes a destination and the
     * ordinary infantry layer supplies the conduct.
     *
     * <p>Nothing here steers anybody: the craft waits, {@code AirSystem} takes
     * aboard whoever reaches the ramp, and the sortie leaves when it is full or
     * when there is no one else coming.
     *
     * @return false when there is no airbase to march to or nowhere to march
     *         from, leaving the sortie loaded as it always was
     */
    private boolean embarkOnPad(BattleControl sim, ReinforcementRequest req,
                                DeliveryDeployment deployment,
                                ShuttleMission mission) {
        if (airbaseNode(sim, req) == null) return false;
        int[] primary = WalkInMeans.pickPrimaryCell(sim, req, axis);
        if (primary == null) return false;
        List<int[]> cells = WalkInMeans.collectAdjacentCells(sim.getGrid(),
                new LandingZoneScorer(sim.getGrid(), sim.getTopology()),
                primary[0], primary[1], DEFAULT_TYPE.capacity);
        if (cells.isEmpty()) return false;

        GroundRosterProfile effectiveRoster = groundRoster != null
                ? groundRoster : sim.getGroundRoster();
        UnitType infantryType = effectiveRoster != null
                ? effectiveRoster.unitType(GroundRosterProfile.ForceTier.ELITE)
                : FactionUnitRoster.forFaction(req.side).elite();

        // The ramp, not the field. This is the same point AirSystem measures
        // its boarding reach from, so "where the crew was sent" and "where the
        // crew is taken aboard" cannot drift apart.
        int padX = (int) Math.floor(mission.entryX);
        int padY = (int) Math.floor(mission.entryY);

        Squad squad = null;
        int marched = 0;
        for (int[] cell : cells) {
            EntitySpec unit = new EntitySpec("e" + (nextEmbarkId++), req.side,
                    infantryType, cell[0], cell[1]);
            if (effectiveRoster != null) {
                InfantryLoadoutRolls.defenderLoadout(effectiveRoster,
                        GroundRosterProfile.ForceTier.ELITE, risk, sim.random()).seedInto(unit);
            } else {
                InfantryLoadoutRolls.defenderSquad(
                        1, infantryType, risk, sim.random())[0].seedInto(unit);
            }
            unit.role(UnitRole.PATROL);
            if (squad == null) {
                int sid = sim.mintSquad(req.side, infantryType);
                SquadCommandClaim.reinforcement(req.reason.name()).apply(sim,
                        ObjectiveAssignment.defendSite(sid, padX, padY));
                squad = sim.getSquad(sid);
            }
            if (squad != null) unit.squad(squad.id);
            sim.spawn(unit);
            marched++;
        }
        if (squad == null) return false;
        squad.originalSize = marched;

        mission.state = ShuttleState.LOADING;
        mission.marinesRemaining = 0;
        mission.embarkSquadId = squad.id;
        mission.boardingPatience = BOARDING_PATIENCE;
        // Whoever the lift does not take stops being the air arm's the moment
        // the sortie closes, and becomes the commander's like any other squad.
        mission.embarkHandoff = deploymentPolicy != null
                ? deployment.squadClaim() : null;
        return true;
    }

    /**
     * Entry and exit for a sortie flying off a berth: that berth's pad, both
     * ways. An aircraft based somewhere leaves from where it stands and comes
     * home to the same place.
     */
    private static float[] sortieFromBerth(AirfieldService.Berth berth) {
        float padX = berth.centerX + 0.5f;
        float padY = berth.centerY + 0.5f;
        return new float[]{ padX, padY, padX, padY };
    }

    /** This side's airbase, which is where its aircraft are and where a crew walks to. */
    private static TacticalNode airbaseNode(BattleControl sim, ReinforcementRequest req) {
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return null;
        List<TacticalNode> near = map.nearest(req.rallyX, req.rallyY, 1,
                EnumSet.of(TacticalNode.Kind.AIRBASE));
        return near.isEmpty() ? null : near.get(0);
    }

    /**
     * Where to look for a landing zone: the defender's safe band when there is
     * an authority to ask, and the raw rally otherwise.
     *
     * <p>This is the whole of "don't land behind their lines". The rally says
     * where the force is wanted and the objective says what it is for; neither
     * says where an aircraft can survive touching down, and on a losing track
     * the answer to all three used to be the same cell.
     */
    private int[] deliveryCentre(ReinforcementRequest req) {
        DeliveryDeployment deployment = deploymentFor(req);
        return new int[]{ deployment.hintX(), deployment.hintY() };
    }

    /**
     * This request's delivery terms: where the aircraft may put down, who owns
     * the squad it carries, and whether the objective it was sent for is a real
     * objective or only a starting point.
     *
     * <p>Never null. A battle with no commanding authority to ask falls back to
     * the same legacy terms the convoy uses — the raw rally, no rear standoff,
     * and reinforcement ownership — which is what this means did unconditionally
     * before there was a policy to ask.
     */
    private DeliveryDeployment deploymentFor(ReinforcementRequest req) {
        if (deploymentPolicy == null) return DeliveryDeployment.legacy(req);
        DeliveryDeployment deployment = deploymentPolicy.deploymentFor(req);
        return deployment != null ? deployment : DeliveryDeployment.legacy(req);
    }

    /**
     * The off-map point a sortie for this side crosses on at, and the one it
     * leaves by.
     *
     * <p>Deferred to {@link MapEntry}, which is where "which edge is this
     * side's rear" lives for everything that arrives — on foot or in the air.
     */
    private static float[] entryForSide(Faction side, TraversalAxis axis,
                                        float lzX, float lzY, int gridW, int gridH) {
        return MapEntry.airForSide(side, axis, lzX, lzY, gridW, gridH);
    }
}
