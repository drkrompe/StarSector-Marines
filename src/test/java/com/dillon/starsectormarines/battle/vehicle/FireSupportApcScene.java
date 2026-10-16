package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The vehicle variant ladder's acceptance: a {@link VehicleType#FIRE_SUPPORT_APC} standing off at range shells an
 * enemy-held position while a {@link VehicleType#HEAVY_APC} drives a four-marine delivery up beside it and unloads.
 *
 * <p>Two loops on one world. {@code shelled} has the mortar carrier parked a little over thirty cells from the
 * position — inside the heavy mortar's 36, outside the defenders' rifles — and {@code control} is the same delivery
 * with no fire-support hull at all. The troop carrier's own roof MG and the deboarded marines also hurt the
 * position, so "the mortar did damage" is read as the difference between the two loops at the moment the troop
 * carrier lands, not as the position's wear on its own.
 *
 * <p>Both carriers are production pieces: the troop APC runs an ordinary {@link VehicleMission} delivery (inbound,
 * {@code LANDED} on its deboard cadence, outbound), and the mortar carrier is a {@link VehicleState#DEPLOYED} chassis
 * whose roof turret acquires on its own through {@link GroundVehicleTurretSystem}. Nothing here aims a round.
 */
public final class FireSupportApcScene implements BehaviorScene {

    public static final String SHELLED = "shelled";
    public static final String CONTROL = "control";

    static final int WIDTH = 96;
    static final int HEIGHT = 48;
    /** Centre of the defended position. */
    public static final float POSITION_X = 66f;
    public static final float POSITION_Y = 24f;
    /** Where the troop carrier sets its squad down: beside the position, on its south-west shoulder. */
    public static final float LZ_X = 60.5f;
    public static final float LZ_Y = 31.5f;
    /** The mortar carrier's stand-off: about 33 cells from the position. */
    public static final float FIRE_SUPPORT_X = 34.5f;
    public static final float FIRE_SUPPORT_Y = 14.5f;

    /** The observers: about 26 cells short of the position, inside a marine's 36 of sight, outside a rifle's 22. */
    public static final int SPOTTER_X = 40;
    public static final int SPOTTER_Y = 22;

    private static final int DEFENDER_TEAMS = 3;
    private static final int TEAM_SIZE = 4;
    /**
     * Heavy plate on every defender — the stand-in for a dug-in position. Its rating sits above the roof MG's
     * penetration (4) and below the heavy mortar's (10), which is the trade the fire-support variant exists for.
     */
    private static final float DEFENDER_ARMOR = 100f;
    private static final float DEFENDER_ARMOR_RATING = 10f;
    private static final int TICKS = 1200;
    /** A round counts as on the position when it lands this close to its centre. */
    private static final float ON_POSITION_RADIUS = 4f;
    /** A marine counts as set down beside the position when it lands this close to its centre. */
    private static final float BESIDE_RADIUS = 12f;
    private static final String RIFLE = "weapon.field-rifle";
    /** The heavy mortar's burst radius plus a cell: a round this close to a friend is a friendly-fire risk. */
    private static final float FRIENDLY_DANGER_RADIUS = 2.35f;

    private final int frameEvery;

    public FireSupportApcScene() {
        this(15);
    }

    public FireSupportApcScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "fire-support-apc"; }

    @Override public String label() {
        return "Fire-support APC: a mortar carrier standing off at range shells a position while a troop APC unloads "
                + "a squad beside it; the control is the same delivery with no mortar carrier";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        Trace shelled = run(SHELLED, true, frames);
        Trace control = run(CONTROL, false, frames);
        List<SceneReport> reports = new ArrayList<>();
        reports.add(report(SHELLED, shelled, control));
        reports.add(report(CONTROL, control, shelled));
        return reports;
    }

    private Trace run(String loop, boolean fireSupport, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                // A sealed box in the far corner: the one defender nobody can see or reach, so the side stays
                // present if the position falls.
                .wall(86, 38, 94, 38).wall(86, 46, 94, 46).wall(86, 38, 86, 46).wall(94, 38, 94, 46)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 90, 42)
                // Two marines lying up out of rifle reach with eyes on the position. The carriers reveal nothing to
                // the player's fog, so without them the screen would show shells falling on ground the player
                // cannot see. They are in both loops, and nothing they carry reaches the position.
                .squad("spotters").size(2).at(SPOTTER_X, SPOTTER_Y).primary(RIFLE).stationary().done();
        // Rooted fire teams in heavy plate, two cells between posts so one shell cannot take a whole team.
        for (int team = 0; team < DEFENDER_TEAMS; team++) {
            builder.squad(teamKey(team)).faction(Faction.DEFENDER).size(TEAM_SIZE)
                    .inFile((int) POSITION_X - 3, (int) POSITION_Y + 3 - 3 * team, 2, 0)
                    .primary(RIFLE).armor(DEFENDER_ARMOR, DEFENDER_ARMOR_RATING).stationary();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();

        // The troop carrier: an ordinary delivery from the west edge, set down beside the position, then away
        // north-east, off the map and away from the guns.
        float[] inX = {28.5f, 40.5f, LZ_X};
        float[] inY = {LZ_Y, LZ_Y, LZ_Y};
        float[] outX = {LZ_X, 66.5f, 66.5f};
        float[] outY = {LZ_Y, 40.5f, HEIGHT + 6f};
        VehicleMission delivery = new VehicleMission(inX, inY, outX, outY, 0f, VehicleType.HEAVY_APC.capacity);
        MarineLoadout[] kit = new MarineLoadout[VehicleType.HEAVY_APC.capacity];
        for (int i = 0; i < kit.length; i++) kit[i] = new MarineLoadout(UnitRole.COMBATANT, null, RIFLE, null, 0);
        delivery.marineLoadout = kit;
        long troop = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE, delivery);

        long support = 0L;
        if (fireSupport) {
            support = sim.convoy().spawn(VehicleType.FIRE_SUPPORT_APC, Faction.MARINE,
                    VehicleMission.deployed(FIRE_SUPPORT_X, FIRE_SUPPORT_Y));
            sim.convoy().body(support).teleport(FIRE_SUPPORT_X, FIRE_SUPPORT_Y, 0f);
            sim.convoy().turret(support).facingDeg = 0f;
        }

        Trace trace = new Trace(world, troop, support);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption());
        run.run(TICKS);
        trace.observe(sim, TICKS);
        sim.close();
        return trace;
    }

    private SceneReport report(String loop, Trace t, Trace other) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("troop-apc-landed", t.landedTick >= 0,
                "troop APC reached LANDED at tick " + t.landedTick));
        verdicts.add(Verdict.of("unloaded-beside", t.deboarded == VehicleType.HEAVY_APC.capacity
                        && t.farthestDeboardFromPosition <= BESIDE_RADIUS,
                t.deboarded + " of " + VehicleType.HEAVY_APC.capacity + " marines set down, farthest "
                        + round(t.farthestDeboardFromPosition) + " cells from the position centre (limit "
                        + BESIDE_RADIUS + ")"));
        if (t.support != 0L) {
            verdicts.add(Verdict.of("mortar-fired", t.mortarRounds > 0,
                    t.mortarRounds + " rounds left the fire-support APC's tube, first at tick " + t.firstRoundTick));
            verdicts.add(Verdict.of("shells-on-position", t.mortarRoundsOnPosition > 0,
                    t.mortarRoundsOnPosition + " of " + t.mortarRounds + " rounds landed within "
                            + ON_POSITION_RADIUS + " cells of the position centre"));
            // Read before the troop carrier's roof MG first fires, when nothing else on the map can reach the
            // position: whatever wear there is then is the mortar's, and the control says what "nothing" reads.
            verdicts.add(Verdict.of("mortar-damage", t.wearBeforeTroopFire > 0.5f
                            && t.wearBeforeTroopFire > other.wearBeforeTroopFire,
                    "position wear before the troop APC first fired (tick " + t.troopFirstShotTick + "): "
                            + round(t.wearBeforeTroopFire) + " defender-equivalents, " + t.killedBeforeTroopFire
                            + " down, against the control's " + round(other.wearBeforeTroopFire) + " (tick "
                            + other.troopFirstShotTick + "); at landing " + round(t.wearAtLanding) + " against "
                            + round(other.wearAtLanding)));
            verdicts.add(Verdict.of("no-passengers", t.supportCapacity == 0 && t.supportManifestMax == 0
                            && !t.supportMintedSquad,
                    "fire-support APC capacity " + t.supportCapacity + ", most ever aboard "
                            + t.supportManifestMax + ", minted a squad: " + t.supportMintedSquad));
        } else {
            verdicts.add(Verdict.of("no-mortar", t.mortarRounds == 0,
                    "no fire-support hull, " + t.mortarRounds + " mortar rounds"));
        }
        Map<String, Number> readings = new LinkedHashMap<>();
        readings.put("mortarRounds", t.mortarRounds);
        readings.put("mortarRoundsOnPosition", t.mortarRoundsOnPosition);
        readings.put("firstRoundTick", t.firstRoundTick);
        readings.put("supportAmmoLeft", t.supportAmmoLeft);
        readings.put("troopApcShots", t.troopShots);
        readings.put("troopFirstShotTick", t.troopFirstShotTick);
        readings.put("wearBeforeTroopFire", round(t.wearBeforeTroopFire));
        readings.put("killedBeforeTroopFire", t.killedBeforeTroopFire);
        readings.put("mortarRoundsNearFriendlies", t.mortarRoundsNearFriendlies);
        readings.put("troopApcWreckedTick", t.troopWreckedTick);
        readings.put("landedTick", t.landedTick);
        readings.put("troopApcFinalX", round(t.troopX));
        readings.put("troopApcFinalY", round(t.troopY));
        readings.put("troopApcFinalState", t.troopState == null ? -1 : t.troopState.ordinal());
        readings.put("lastDeboardTick", t.lastDeboardTick);
        readings.put("deboarded", t.deboarded);
        readings.put("farthestDeboardFromPosition", round(t.farthestDeboardFromPosition));
        readings.put("wearAtLanding", round(t.wearAtLanding));
        readings.put("killedAtLanding", t.killedAtLanding);
        readings.put("wearAtEnd", round(t.wear));
        readings.put("defendersAliveAtEnd", t.defendersAlive);
        readings.put("deboardedAliveAtEnd", t.deboardedAlive);
        return new SceneReport(id(), loop, TICKS, verdicts, readings);
    }

    private static String teamKey(int team) {
        return "position-" + team;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static final class Trace implements TickObserver {
        final long troop;
        final long support;
        final long[] defenders;
        final float[] fullDurability;
        final Set<Long> deboardedIds = new HashSet<>();
        final List<Long> deboardedOrder = new ArrayList<>();
        int supportCapacity;
        int supportManifestMax;
        boolean supportMintedSquad;
        int supportAmmoLeft = -1;
        int mortarRounds;
        int mortarRoundsOnPosition;
        int firstRoundTick = -1;
        int troopShots;
        int mortarRoundsNearFriendlies;
        int troopWreckedTick = -1;
        int troopFirstShotTick = -1;
        float wearBeforeTroopFire = -1f;
        int killedBeforeTroopFire = -1;
        int landedTick = -1;
        int lastDeboardTick = -1;
        int deboarded;
        int deboardedAlive;
        float farthestDeboardFromPosition;
        float wear;
        int killed;
        float wearAtLanding = -1f;
        int killedAtLanding = -1;
        int defendersAlive;
        float troopX;
        float troopY;
        VehicleState troopState;
        private String caption = "";

        Trace(SceneWorld world, long troop, long support) {
            this.troop = troop;
            this.support = support;
            this.defenders = new long[DEFENDER_TEAMS * TEAM_SIZE];
            for (int team = 0; team < DEFENDER_TEAMS; team++) {
                System.arraycopy(world.members(teamKey(team)), 0, defenders, team * TEAM_SIZE, TEAM_SIZE);
            }
            this.fullDurability = new float[defenders.length];
            BattleSimulation sim = world.sim();
            for (int i = 0; i < defenders.length; i++) {
                long id = defenders[i];
                fullDurability[i] = sim.world().maxHp(id)
                        + (sim.world().hasArmor(id) ? sim.world().maxArmor(id) : 0f);
            }
            if (support != 0L) supportCapacity = sim.convoy().vehicleType(support).capacity;
        }

        String caption() {
            return caption;
        }

        /** Whether a round aimed here lands within its own burst of a live marine or the troop carrier. */
        private boolean nearFriendly(BattleSimulation sim, float x, float y) {
            if (sim.convoy().isVehicle(troop) && sim.convoy().isTargetable(troop)
                    && Math.hypot(sim.convoy().body(troop).x - x, sim.convoy().body(troop).y - y)
                    <= FRIENDLY_DANGER_RADIUS + 1f) {
                return true;
            }
            for (int i = 0, n = sim.getRoster().liveCount(); i < n; i++) {
                long id = sim.getRoster().get(i);
                if (sim.convoy().isVehicle(id) || sim.identity().faction(id) != Faction.MARINE
                        || !sim.world().isAlive(id)) continue;
                if (Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y) <= FRIENDLY_DANGER_RADIUS) return true;
            }
            return false;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (support != 0L && shot.shooterId == support) {
                    mortarRounds++;
                    if (firstRoundTick < 0) firstRoundTick = tick;
                    if (Math.hypot(shot.toX - POSITION_X, shot.toY - POSITION_Y) <= ON_POSITION_RADIUS) {
                        mortarRoundsOnPosition++;
                    }
                    if (nearFriendly(sim, shot.toX, shot.toY)) mortarRoundsNearFriendlies++;
                } else if (shot.shooterId == troop) {
                    troopShots++;
                    if (troopFirstShotTick < 0) {
                        // The wear read on the previous tick: nothing the troop carrier fired is in it yet.
                        troopFirstShotTick = tick;
                        wearBeforeTroopFire = wear;
                        killedBeforeTroopFire = killed;
                    }
                }
            }

            float worn = 0f;
            int down = 0;
            int alive = 0;
            for (int i = 0; i < defenders.length; i++) {
                long id = defenders[i];
                if (!sim.world().isAlive(id)) {
                    worn += 1f;
                    down++;
                    continue;
                }
                alive++;
                float left = sim.world().hp(id) + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
                if (fullDurability[i] > 0f) worn += 1f - left / fullDurability[i];
            }
            wear = worn;
            killed = down;
            defendersAlive = alive;

            VehicleMission delivery = sim.convoy().isVehicle(troop) ? sim.convoy().mission(troop) : null;
            if (delivery != null && delivery.state == VehicleState.WRECKED && troopWreckedTick < 0) {
                troopWreckedTick = tick;
            }
            if (delivery != null) {
                troopX = sim.convoy().body(troop).x;
                troopY = sim.convoy().body(troop).y;
                troopState = delivery.state;
            }
            if (delivery != null && landedTick < 0 && delivery.state == VehicleState.LANDED) {
                landedTick = tick;
                wearAtLanding = wear;
                killedAtLanding = killed;
            }
            if (delivery != null && delivery.squadId != Squad.NO_SQUAD) {
                for (int i = 0, n = sim.squadMemberCount(delivery.squadId); i < n; i++) {
                    long id = sim.squadMemberAt(delivery.squadId, i);
                    if (deboardedIds.add(id)) {
                        deboardedOrder.add(id);
                        deboarded++;
                        lastDeboardTick = tick;
                        farthestDeboardFromPosition = Math.max(farthestDeboardFromPosition, (float) Math.hypot(
                                sim.world().x(id) - POSITION_X, sim.world().y(id) - POSITION_Y));
                    }
                }
            }
            int out = 0;
            for (long id : deboardedOrder) if (sim.world().isAlive(id)) out++;
            deboardedAlive = out;

            if (support != 0L && sim.convoy().isVehicle(support)) {
                supportManifestMax = Math.max(supportManifestMax, sim.transport().manifest(support).size());
                supportMintedSquad |= sim.convoy().mission(support).squadId != Squad.NO_SQUAD;
                supportAmmoLeft = sim.convoy().turret(support).ammo;
            }

            String stateName = delivery == null ? "gone" : delivery.state.name().toLowerCase(Locale.ROOT);
            caption = String.format(Locale.ROOT,
                    "%s / position %d of %d standing, wear %.2f / troop APC %s, %d set down%s",
                    support != 0L ? "mortar rounds " + mortarRounds + " (" + mortarRoundsOnPosition + " on position)"
                            : "no fire support",
                    alive, defenders.length, wear, stateName, deboarded,
                    deboarded > 0 ? " (" + deboardedAlive + " alive)" : "");
        }
    }
}
