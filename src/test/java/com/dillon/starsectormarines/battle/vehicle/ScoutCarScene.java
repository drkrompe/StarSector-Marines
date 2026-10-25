package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The scout car's acceptance: a {@link VehicleType#SCOUT_CAR} parked on a flank vantage sees a defended position the
 * marine squad behind the long wall cannot, and what it sees reaches the commander and the player's fog without
 * reaching any squad's beliefs or any hit roll.
 *
 * <p>Four loops on one world. {@code scouted} parks the car with open line of sight to the position inside its 48
 * cells of sight. {@code walled-in} parks it on the same cell with a wall across its view, because its sight is
 * ordinary grid line of sight and that is its give-up. {@code control} has no car at all. Nothing in the scene can
 * shoot anything: the squad is walled off and far out of reach, the car carries no gun and stands outside the
 * defenders' rifles. The scene is about information, not combat. {@code crew} parks an unarmed hull with no mast on the
 * same vantage: every hull looks, out to a marine's sight, so it sees the nearest of the file and not the farthest.
 *
 * <p>The commander snapshot is read through {@link BattleSimulation#getCommanderInfluence}, the production read,
 * which also requests its refresh on the ordinary 15-tick cadence.
 */
public final class ScoutCarScene implements BehaviorScene {

    public static final String SCOUTED = "scouted";
    public static final String WALLED_IN = "walled-in";
    public static final String CONTROL = "control";
    public static final String CREW = "crew";

    static final int WIDTH = 96;
    static final int HEIGHT = 56;
    /** The squad, west of the long wall and more than a marine's sight away from the position. */
    public static final int SQUAD_X = 14;
    public static final int SQUAD_Y = 40;
    /** The long wall screening the squad: x = 36, from y = 14 to the north edge. */
    public static final int SCREEN_X = 36;
    public static final int SCREEN_Y0 = 14;
    /** The hidden position: a four-man file from (68, 42) east, two cells apart. */
    public static final int POSITION_X = 68;
    public static final int POSITION_Y = 42;
    private static final int POSITION_SIZE = 4;
    /** The car's flank vantage: about 37 cells from the far end of the file, outside a rifle's 22. */
    public static final float SCOUT_X = 56.5f;
    public static final float SCOUT_Y = 9.5f;
    /** The walled-in loop's blind: a wall across the car's whole view of the position. */
    public static final int BLIND_Y = 16;
    public static final int BLIND_X0 = 37;
    public static final int BLIND_X1 = 85;
    /** The cells counted as "the position" for fog readings. */
    static final int BOX_X0 = POSITION_X - 3;
    static final int BOX_X1 = POSITION_X + 2 * (POSITION_SIZE - 1) + 3;
    static final int BOX_Y0 = POSITION_Y - 3;
    static final int BOX_Y1 = POSITION_Y + 3;

    private static final int TICKS = 600;
    private static final String RIFLE = "weapon.field-rifle";

    private final int frameEvery;

    public ScoutCarScene() {
        this(15);
    }

    public ScoutCarScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "scout-car"; }

    @Override public String label() {
        return "Scout car: a gunless hull on a flank vantage sees a position the walled-off squad cannot; the commander "
                + "and the player's fog learn of it, the squad's beliefs do not. Walled-in and no-scout controls";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        Trace scouted = run(SCOUTED, VehicleType.SCOUT_CAR, false, frames);
        Trace walled = run(WALLED_IN, VehicleType.SCOUT_CAR, true, frames);
        Trace control = run(CONTROL, null, false, frames);
        Trace crew = run(CREW, VehicleType.CARGO_CARRIER, false, frames);
        List<SceneReport> reports = new ArrayList<>();
        reports.add(reportScouted(scouted));
        reports.add(reportWalled(walled));
        reports.add(reportControl(control));
        reports.add(reportCrew(crew));
        return reports;
    }

    private Trace run(String loop, VehicleType hull, boolean blind, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                // The long wall between the squad and everything east of it.
                .wall(SCREEN_X, SCREEN_Y0, SCREEN_X, HEIGHT - 1)
                // A sealed box in the far corner: the one defender nobody can see or reach.
                .wall(86, 46, 94, 46).wall(86, 54, 94, 54).wall(86, 46, 86, 54).wall(94, 46, 94, 54)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 90, 50)
                .squad("squad").size(6).at(SQUAD_X, SQUAD_Y).primary(RIFLE).stationary().done()
                .squad("position").faction(Faction.DEFENDER).size(POSITION_SIZE)
                        .inFile(POSITION_X, POSITION_Y, 2, 0).primary(RIFLE).stationary().done();
        if (blind) builder.wall(BLIND_X0, BLIND_Y, BLIND_X1, BLIND_Y);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();

        long car = 0L;
        if (hull != null) {
            car = sim.convoy().spawn(hull, Faction.MARINE, VehicleMission.deployed(SCOUT_X, SCOUT_Y));
            sim.convoy().body(car).teleport(SCOUT_X, SCOUT_Y, 0f);
        }

        Trace trace = new Trace(world, car);
        SceneRun run = SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption());
        run.run(TICKS);
        sim.close();
        return trace;
    }

    private SceneReport reportScouted(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(infantryBlind(t));
        verdicts.add(Verdict.of("scout-sighted", t.everSighted.size() == t.hidden.length,
                t.everSighted.size() + " of " + t.hidden.length + " hidden defenders sighted by the car, first at tick "
                        + t.firstSightedTick + ", at most " + t.maxSightedAtOnce + " at once"));
        verdicts.add(Verdict.of("commander-knows", t.commanderKnew.size() == t.hidden.length
                        && t.commanderContactWithSquad == 0,
                t.commanderKnew.size() + " of " + t.hidden.length + " hidden defenders in the MARINE commander's "
                        + "contacts with reporter -1, first at tick " + t.commanderContactTick
                        + "; contacts naming a squad as reporter: " + t.commanderContactWithSquad));
        verdicts.add(Verdict.of("fog-revealed", t.everRevealedDefender.size() == t.hidden.length,
                t.everRevealedDefender.size() + " of " + t.hidden.length + " hidden defenders' cells revealed to the "
                        + "player, first at tick " + t.firstRevealedTick + "; position box peak "
                        + t.maxBoxRevealed + " of " + t.boxCells + " cells"));
        verdicts.add(notACombatBuff(t));
        return new SceneReport(id(), SCOUTED, TICKS, verdicts, readings(t));
    }

    private SceneReport reportWalled(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("sees-nothing-through-walls", t.everSighted.isEmpty() && t.commanderKnew.isEmpty()
                        && t.everRevealedDefender.isEmpty(),
                "with a wall across its view the car sighted " + t.everSighted.size() + " hidden defenders, the "
                        + "commander held " + t.commanderKnew.size() + ", the fog revealed " + t.everRevealedDefender.size()
                        + " of their cells (position box peak " + t.maxBoxRevealed + "); the car's own sight source "
                        + "still revealed " + t.maxRevealedNearScout + " cells around it"));
        verdicts.add(Verdict.of("scout-sight-source-live", t.maxRevealedNearScout > 0,
                t.maxRevealedNearScout + " cells within 6 of the car revealed at peak: the fog channel is publishing, "
                        + "the wall is what stops it"));
        verdicts.add(infantryBlind(t));
        verdicts.add(notACombatBuff(t));
        return new SceneReport(id(), WALLED_IN, TICKS, verdicts, readings(t));
    }

    private SceneReport reportCrew(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        int sighted = t.everSighted.size();
        verdicts.add(Verdict.of("crew-sees-within-a-marines-sight", sighted > 0 && sighted < t.hidden.length,
                "the unarmed hull sighted " + sighted + " of " + t.hidden.length + " hidden defenders: those inside "
                        + VehicleType.CREW_SIGHT_CELLS + " cells, not the file's far end"));
        verdicts.add(Verdict.of("commander-knows-what-crew-saw", t.commanderKnew.equals(t.everSighted)
                        && t.commanderContactWithSquad == 0,
                t.commanderKnew.size() + " hidden defenders in the commander's contacts with reporter -1, against "
                        + sighted + " sighted"));
        verdicts.add(Verdict.of("fog-revealed-near", t.everRevealedDefender.size() >= sighted
                        && t.everRevealedDefender.size() < t.hidden.length,
                t.everRevealedDefender.size() + " of " + t.hidden.length + " hidden defenders' cells revealed to the "
                        + "player; the hull's own footprint is a contributor's"));
        verdicts.add(infantryBlind(t));
        return new SceneReport(id(), CREW, TICKS, verdicts, readings(t));
    }

    private SceneReport reportControl(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(infantryBlind(t));
        verdicts.add(Verdict.of("commander-lacks", t.commanderKnew.isEmpty(),
                "no scout: the MARINE commander held " + t.commanderKnew.size() + " of the hidden defenders across "
                        + t.commanderSnapshots + " published snapshots"));
        verdicts.add(Verdict.of("position-fogged", t.everRevealedDefender.isEmpty() && t.maxBoxRevealed == 0,
                "no scout: " + t.everRevealedDefender.size() + " hidden defenders' cells revealed, position box peak "
                        + t.maxBoxRevealed + " of " + t.boxCells));
        return new SceneReport(id(), CONTROL, TICKS, verdicts, readings(t));
    }

    private static Verdict infantryBlind(Trace t) {
        return Verdict.of("infantry-blind", t.squadBelievedHidden.isEmpty(),
                "marine squad beliefs held " + t.squadBelievedHidden.size() + " of the " + t.hidden.length
                        + " hidden defenders at any tick (" + t.squadBeliefTicks + " ticks with any belief at all)");
    }

    private static Verdict notACombatBuff(Trace t) {
        return Verdict.of("not-a-combat-buff", t.scoutShots == 0 && t.hiddenDamage == 0f,
                "the car fired " + t.scoutShots + " shots; the hidden defenders lost " + round(t.hiddenDamage)
                        + " durability (all shots on the map: " + t.allShots + ")");
    }

    private static Map<String, Number> readings(Trace t) {
        Map<String, Number> readings = new LinkedHashMap<>();
        readings.put("hiddenDefenders", t.hidden.length);
        readings.put("sightedEver", t.everSighted.size());
        readings.put("maxSightedAtOnce", t.maxSightedAtOnce);
        readings.put("sightingTicks", t.sightingTicks);
        readings.put("firstSightedTick", t.firstSightedTick);
        readings.put("commanderContactTick", t.commanderContactTick);
        readings.put("commanderKnewHidden", t.commanderKnew.size());
        readings.put("commanderSnapshots", t.commanderSnapshots);
        readings.put("firstRevealedTick", t.firstRevealedTick);
        readings.put("revealedDefenderCells", t.everRevealedDefender.size());
        readings.put("positionBoxCells", t.boxCells);
        readings.put("positionBoxRevealedPeak", t.maxBoxRevealed);
        readings.put("positionBoxRevealedAtEnd", t.boxRevealedNow);
        readings.put("revealedNearScoutPeak", t.maxRevealedNearScout);
        readings.put("squadBelievedHidden", t.squadBelievedHidden.size());
        readings.put("squadBeliefTicks", t.squadBeliefTicks);
        readings.put("scoutShots", t.scoutShots);
        readings.put("allShots", t.allShots);
        readings.put("hiddenDamage", round(t.hiddenDamage));
        return readings;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static final class Trace implements TickObserver {
        final long car;
        final long[] hidden;
        final float[] fullDurability;
        final Squad squad;
        final Set<Long> hiddenSet = new HashSet<>();
        final Set<Long> everSighted = new HashSet<>();
        final Set<Long> commanderKnew = new HashSet<>();
        final Set<Long> everRevealedDefender = new HashSet<>();
        final Set<Long> squadBelievedHidden = new HashSet<>();
        final int boxCells = (BOX_X1 - BOX_X0 + 1) * (BOX_Y1 - BOX_Y0 + 1);
        int firstSightedTick = -1;
        int maxSightedAtOnce;
        int sightingTicks;
        int commanderContactTick = -1;
        int commanderContactWithSquad;
        int commanderSnapshots;
        private int lastSnapshotTick = Integer.MIN_VALUE;
        int firstRevealedTick = -1;
        int maxBoxRevealed;
        int boxRevealedNow;
        int maxRevealedNearScout;
        int squadBeliefTicks;
        int scoutShots;
        int allShots;
        float hiddenDamage;
        private String caption = "";

        Trace(SceneWorld world, long car) {
            this.car = car;
            this.hidden = world.members("position");
            this.squad = world.squad("squad");
            BattleSimulation sim = world.sim();
            this.fullDurability = new float[hidden.length];
            for (int i = 0; i < hidden.length; i++) {
                long id = hidden[i];
                hiddenSet.add(id);
                fullDurability[i] = sim.world().maxHp(id) + (sim.world().hasArmor(id) ? sim.world().maxArmor(id) : 0f);
            }
        }

        String caption() {
            return caption;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                allShots++;
                if (car != 0L && shot.shooterId == car) scoutShots++;
            }

            int sightedNow = 0;
            for (long id : hidden) {
                if (sim.hullSightings().sighted(Faction.MARINE, id)) {
                    sightedNow++;
                    everSighted.add(id);
                }
            }
            if (sightedNow > 0) {
                sightingTicks++;
                if (firstSightedTick < 0) firstSightedTick = tick;
            }
            maxSightedAtOnce = Math.max(maxSightedAtOnce, sightedNow);

            CommanderInfluenceSnapshot snapshot = sim.getCommanderInfluence(Faction.MARINE);
            if (snapshot != null) {
                if (snapshot.updatedTick() != lastSnapshotTick) {
                    lastSnapshotTick = snapshot.updatedTick();
                    commanderSnapshots++;
                }
                for (CommanderContact contact : snapshot.contacts()) {
                    if (!hiddenSet.contains(contact.unitId())) continue;
                    if (contact.reporterSquadId() == -1) {
                        if (commanderKnew.add(contact.unitId()) && commanderContactTick < 0) {
                            commanderContactTick = tick;
                        }
                    } else {
                        commanderContactWithSquad++;
                    }
                }
            }

            FogOfWarService fog = sim.getFogOfWar();
            for (long id : hidden) {
                if (!sim.world().isAlive(id)) continue;
                if (fog.isCellRevealed((int) Math.floor(sim.world().x(id)), (int) Math.floor(sim.world().y(id)))
                        && everRevealedDefender.add(id) && firstRevealedTick < 0) {
                    firstRevealedTick = tick;
                }
            }
            int box = 0;
            for (int y = BOX_Y0; y <= BOX_Y1; y++) {
                for (int x = BOX_X0; x <= BOX_X1; x++) if (fog.isCellRevealed(x, y)) box++;
            }
            boxRevealedNow = box;
            maxBoxRevealed = Math.max(maxBoxRevealed, box);
            if (car != 0L) {
                int cx = (int) Math.floor(SCOUT_X);
                int cy = (int) Math.floor(SCOUT_Y);
                int near = 0;
                for (int y = cy - 6; y <= cy + 6; y++) {
                    for (int x = cx - 6; x <= cx + 6; x++) if (fog.isCellRevealed(x, y)) near++;
                }
                maxRevealedNearScout = Math.max(maxRevealedNearScout, near);
            }

            List<BelievedContact> beliefs = squad.believedContacts();
            if (!beliefs.isEmpty()) squadBeliefTicks++;
            for (BelievedContact belief : beliefs) {
                if (hiddenSet.contains(belief.unitId())) squadBelievedHidden.add(belief.unitId());
            }

            float damage = 0f;
            for (int i = 0; i < hidden.length; i++) {
                long id = hidden[i];
                if (!sim.world().isAlive(id)) {
                    damage += fullDurability[i];
                    continue;
                }
                float left = sim.world().hp(id) + (sim.world().hasArmor(id) ? sim.world().armor(id) : 0f);
                damage += Math.max(0f, fullDurability[i] - left);
            }
            hiddenDamage = damage;

            caption = String.format(Locale.ROOT,
                    "%s / car sees %d of %d / commander holds %d / position box revealed %d of %d / squad believes %d",
                    car == 0L ? "no scout" : "scout car", sightedNow, hidden.length, commanderKnew.size(), box,
                    boxCells, squadBelievedHidden.size());
        }
    }
}
