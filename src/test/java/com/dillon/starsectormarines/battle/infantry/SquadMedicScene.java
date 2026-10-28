package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.testsupport.DiskRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A wounded squad on the march with a Field Medicine officer: <b>does the
 * medic patch people up on the march, holding fire while treating, without
 * stopping the squad?</b> ({@code squad-medic.md}).
 *
 * <p>Eight armored marines march east under a commander's attack move. Four
 * start hurt: one badly with their plate shot away, one moderately, one with
 * only their armor down, and the medic themselves — who treats themselves once
 * nobody worse is in reach. A tough target stands just past the objective inside carbine reach,
 * so the squad has something to shoot at once it arrives and "the medic holds
 * fire while treating" is a reading rather than a vacuous zero.
 *
 * <p><b>The target shoots back.</b> It is issued no kit, but an unequipped
 * defender still fires its unit type's own weapon, so the squad takes about
 * three dozen rounds in every loop. That is left in as the realistic case — a
 * medic working while somebody is shooting — and it means the medic can be
 * killed and replaced. It also means {@code hpLost} is not comparable between
 * loops on its own: a hit on a patched-up marine costs a whole round's damage,
 * where the same hit on somebody at six health costs six. Compare {@code deaths}
 * and the survivors' {@code squadHpEnd} instead.
 *
 * <p>Two loops on the same world. {@code medic} is the shipped behaviour: the
 * patient carries on with the squad and the medic walks at their side while
 * treating; {@code control} is the same squad under an officer without the
 * specialty. The control carries only the verdict that it really had no medic:
 * its numbers exist so the medic loop can be read against them, above all the
 * tick the squad reached its objective.
 *
 * <p>A patient walking out of reach mid-treatment is covered by
 * {@code SquadMedicSystemTest} rather than here: nothing in the shipped game
 * slows one marine against another, so a loop that produced it would be staging
 * the very thing it claimed to observe.
 */
public final class SquadMedicScene implements BehaviorScene {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 32;

    /** A minute: long enough for the march, several treatments and supply recovery to begin. */
    private static final int TICKS = 1800;
    private static final int FRAME_EVERY_TICKS = 15;

    private static final int SQUAD_SIZE = 8;
    private static final int START_X = 10;
    private static final int START_Y = 16;
    private static final int MISSION_X = 60;
    private static final int MISSION_Y = 16;

    /** Within this many cells of the objective the squad counts as there. */
    private static final float ARRIVED_CELLS = 4f;

    /**
     * Past the objective on the march axis and inside the carbine's fourteen
     * cells of it, so the squad gets there and then has something to shoot at.
     */
    private static final int TARGET_X = 72;
    private static final int TARGET_Y = 16;

    private static final String CARBINE = "weapon.smg";
    private static final float ARMOR_CAPACITY = 5f;
    private static final float ARMOR_RATING = 4f;
    private static final String MARINES = "marines";

    /** Ground covered in a tick below which a marine counts as standing still. */
    private static final float STILL_EPSILON = 0.01f;

    /** The wounds, dealt after the first tick has designated the medic. */
    private static final float MEDIC_HP = 10f;
    private static final float BADLY_HURT_HP = 6f;
    private static final float HURT_HP = 14f;
    private static final float ARMOR_ONLY_ARMOR = 1f;

    @Override public String id() { return "squad-medic"; }

    @Override
    public String label() {
        return "Squad medic: does a Field Medicine squad patch up its wounded on the march,"
                + " holding fire while treating and walking with the squad?";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(
                loop(frames, "medic", 1),
                loop(frames, "control", 0));
    }

    private SceneReport loop(FrameSink frames, String loopId, int rank) {
        SceneWorld world = build(rank);
        BattleSimulation sim = world.sim();
        Squad squad = world.squad(MARINES);
        long[] members = world.members(MARINES);
        Instrument instrument = new Instrument(squad, members);

        SceneRun run = SceneRun.of(world)
                .observe(instrument)
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> caption(loopId, squad, instrument, tick));
        // One tick designates the medic; the wounds go round them. The control
        // has no medic, so its wounds land on the marine who would have been.
        run.run(1);
        long expected = expectedMedic(squad, members, sim);
        wound(sim, squad, members, expected);
        instrument.medicUnit = expected;
        run.run(TICKS - 1);

        List<Verdict> verdicts = new ArrayList<>();
        SquadMedic medic = squad.medic;
        if (rank == 0) {
            verdicts.add(Verdict.of("ran-without-a-medic", medic == null,
                    medic == null ? "no squad medic was designated (wanted none)"
                            : "a squad medic was designated under an untrained officer"));
            return new SceneReport(id(), loopId, TICKS, verdicts, instrument.metrics(null));
        }
        verdicts.add(Verdict.of("designated", instrument.firstMedic == expected,
                "the first medic was unit " + instrument.firstMedic
                        + " (wanted unit " + expected + ": the first middle-team squadmate"
                        + " other than the leader)"));
        if (medic == null) {
            return new SceneReport(id(), loopId, TICKS, verdicts, instrument.metrics(null));
        }
        verdicts.add(Verdict.of("treated", medic.started() >= 2,
                medic.started() + " treatment(s) started, " + medic.completed()
                        + " completed, " + medic.interrupted() + " interrupted, " + medic.exhausted()
                        + " ran the pool dry (wanted at least 2 started: two marines start below"
                        + " the health threshold)"));
        verdicts.add(Verdict.of("re-plated", medic.armorReplated() >= 1,
                medic.armorReplated() + " broken plate(s) brought back (wanted at least 1)"));
        verdicts.add(Verdict.of("tends-self", instrument.medicHealed,
                instrument.medicHealed ? "a hurt medic was their own patient and their health rose"
                        : "no medic ever treated themselves, though the medic started hurt"));
        verdicts.add(Verdict.of("held-fire", instrument.shotsWhileTreating == 0,
                instrument.shotsWhileTreating + " round(s) fired by the medic while treating, against "
                        + instrument.medicShots + " over the whole loop (wanted 0 while treating)"));
        SquadMedicine m = SquadMedicine.forRank(rank);
        float restored = medic.healthRestored() + medic.armorRestored();
        float budget = (m.energyMax() + m.energyRegenPerSecond() * TICKS * BattleSimulation.TICK_DT)
                / m.energyPerPoint();
        verdicts.add(Verdict.of("within-energy", restored <= budget + 0.01f,
                String.format(Locale.ROOT, "%.1f point(s) restored against %.1f the pool could pay for"
                        + " (a full pool plus regeneration)", restored, budget)));
        verdicts.add(Verdict.of("kept-moving", instrument.patientMovingTicks > 0,
                instrument.patientMovingTicks + " of " + instrument.treatingTicks
                        + " treating tick(s) had the patient moving (wanted some)"));
        verdicts.add(Verdict.of("no-plan-less-ticks", instrument.planLessTicks <= 1,
                instrument.planLessTicks + " tick(s) with no squad plan (wanted at most the first)"));
        return new SceneReport(id(), loopId, TICKS, verdicts, instrument.metrics(medic));
    }

    private static SceneWorld build(int rank) {
        try {
            DiskRegistries.install();
        } catch (Exception e) {
            throw new IllegalStateException("Could not install the mod's data for the squad-medic scene", e);
        }
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad(MARINES)
                .faction(Faction.MARINE)
                .type(UnitType.MARINE)
                .size(SQUAD_SIZE)
                .at(START_X, START_Y)
                .primary(CARBINE)
                .armor(ARMOR_CAPACITY, ARMOR_RATING)
                .assigned(id -> ObjectiveAssignment.attackMove(id, MISSION_X, MISSION_Y))
                .done();
        // Something to shoot at from the objective, which cannot die. It still
        // fires its type's own weapon; see the class doc.
        builder.squad("target")
                .faction(Faction.DEFENDER)
                .type(UnitType.MARINE_RED)
                .size(1)
                .at(TARGET_X, TARGET_Y)
                .unarmed()
                .stationary()
                .health(1_000_000f)
                .done();
        SceneWorld world = builder.build();

        Squad squad = world.squad(MARINES);
        long[] members = world.members(MARINES);
        squad.leaderId = members[0];
        squad.commandGroupIdentity = new CommandGroupIdentity("scene-officer", "scene-officer",
                "Lt. Scene", true, new OfficerCommandProfile("scene-officer", "Lt. Scene",
                        Rank.LIEUTENANT, 0, Status.ACTIVE, false, 0, 0, 0, rank));
        BattleSimulation sim = world.sim();
        return world;
    }

    /**
     * The production billet, read off the squad as it stands: the first
     * middle-team marine other than the leader, else the first other marine.
     */
    private static long expectedMedic(Squad squad, long[] members, BattleSimulation sim) {
        long fallback = 0L;
        for (long member : members) {
            if (member == squad.leaderId) continue;
            if (sim.squad().fireTeamIndex(member) == 1) return member;
            if (fallback == 0L) fallback = member;
        }
        return fallback;
    }

    /**
     * The medic hurt, then the next three squadmates in seat order: one badly
     * with the plate shot away, one moderately, one with only armor down.
     */
    private static void wound(BattleSimulation sim, Squad squad, long[] members, long medic) {
        sim.world().setHp(medic, MEDIC_HP);
        int dealt = 0;
        for (long member : members) {
            if (member == medic || member == squad.leaderId) continue;
            switch (dealt++) {
                case 0 -> {
                    sim.world().setHp(member, BADLY_HURT_HP);
                    sim.world().setArmor(member, 0f);
                }
                case 1 -> sim.world().setHp(member, HURT_HP);
                case 2 -> sim.world().setArmor(member, ARMOR_ONLY_ARMOR);
                default -> { }
            }
        }
    }

    private static String caption(String loopId, Squad squad, Instrument instrument, int tick) {
        SquadMedic medic = squad.medic;
        String state = medic == null ? "no medic"
                : String.format(Locale.ROOT, "medic %s, energy %.1f, %d done",
                        medic.phase().name().toLowerCase(Locale.ROOT), medic.energy(), medic.completed());
        return String.format(Locale.ROOT, "squad medic [%s]  t=%d  %s  squad hp %.0f",
                loopId, tick, state, instrument.lastSquadHp);
    }

    /** Readings off the squad each tick: who is treating, who moved, who shot. */
    private static final class Instrument implements TickObserver {

        private final Squad squad;
        private final long[] members;
        private final float[] lastX;
        private final float[] lastY;

        long medicUnit;
        long firstMedic;
        private long trackedMedic;
        private float lastMedicHp;
        private boolean medicHealed;
        private int medicReplacements;
        private float lastFriendly;
        private final float[] friendlyByPhase = new float[SquadMedic.Phase.values().length];
        private float friendlyAfterArrival;
        private final float[] lastHp;
        private float hpLost;
        private int deaths;
        private int defenderShots;
        private int shotsWhileTreating;
        private int medicShots;
        private int treatingTicks;
        private int patientMovingTicks;
        private int planLessTicks;
        private int arrivedTick = -1;
        private float startSquadHp = -1f;
        private float lastSquadHp;
        private float lastCentroidX;

        Instrument(Squad squad, long[] members) {
            this.squad = squad;
            this.members = members;
            this.lastX = new float[members.length];
            this.lastY = new float[members.length];
            this.lastHp = new float[members.length];
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            float hp = 0f;
            float cx = 0f;
            int alive = 0;
            for (int i = 0; i < members.length; i++) {
                long unit = sim.resolveUnit(members[i]);
                if (unit == 0L) continue;
                alive++;
                hp += sim.world().hp(unit);
                cx += sim.world().x(unit);
            }
            if (tick == 1) startSquadHp = hp;
            lastSquadHp = hp;
            if (alive > 0) lastCentroidX = cx / alive;
            if (arrivedTick < 0 && alive > 0 && Math.abs(lastCentroidX - MISSION_X) <= ARRIVED_CELLS) {
                arrivedTick = tick;
            }
            if (tick > 0 && squad.currentPlan == null) planLessTicks++;

            // Health actually lost on the tick just played, whatever caused it,
            // and who on the other side fired. Read from the bodies rather than
            // telemetry, which a scene-spawned marine may not carry.
            for (int i = 0; i < members.length; i++) {
                long unit = sim.resolveUnit(members[i]);
                float now = unit == 0L ? 0f : sim.world().hp(unit);
                if (tick > 1 && lastHp[i] > 0f && now < lastHp[i]) {
                    hpLost += lastHp[i] - now;
                    if (now <= 0f) deaths++;
                }
                lastHp[i] = now;
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterFaction == Faction.DEFENDER) defenderShots++;
            }

            // Friendly fire the squad put into itself on the tick just played,
            // filed under what the medic was doing on it.
            float friendly = 0f;
            for (long member : members) {
                if (sim.telemetry().isRecorded(member)) {
                    friendly += sim.telemetry().friendlyFireDamage(member);
                }
            }
            float delta = friendly - lastFriendly;
            lastFriendly = friendly;
            if (delta > 0f) {
                SquadMedic.Phase phase = squad.medic == null ? SquadMedic.Phase.IDLE : squad.medic.phase();
                friendlyByPhase[phase.ordinal()] += delta;
                if (arrivedTick >= 0) friendlyAfterArrival += delta;
            }

            // The medic as of this tick: the designated marine when there is
            // one, else the marine the control would have used.
            SquadMedic current = squad.medic;
            if (current != null && current.medicId() != 0L) medicUnit = current.medicId();
            if (tick == 1) firstMedic = medicUnit;
            long medicLive = medicUnit == 0L ? 0L : sim.resolveUnit(medicUnit);
            if (medicLive != 0L) {
                float medicHp = sim.world().hp(medicLive);
                if (medicLive != trackedMedic) {
                    if (trackedMedic != 0L) medicReplacements++;
                    trackedMedic = medicLive;
                } else if (medicHp > lastMedicHp + 1e-4f) {
                    medicHealed = true;
                }
                if (current != null && current.patientId() == medicLive) medicHealed = true;
                lastMedicHp = medicHp;
            }

            SquadMedic medic = squad.medic;
            boolean treating = medic != null && medic.phase() == SquadMedic.Phase.TREATING;
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (medicUnit == 0L || shot.shooterId != medicUnit) continue;
                medicShots++;
                if (treating) shotsWhileTreating++;
            }
            if (treating) {
                treatingTicks++;
                long patient = sim.resolveUnit(medic.patientId());
                int index = indexOf(medic.patientId());
                if (patient != 0L && index >= 0 && lastX[index] != 0f) {
                    float dx = sim.world().x(patient) - lastX[index];
                    float dy = sim.world().y(patient) - lastY[index];
                    if (Math.sqrt(dx * dx + dy * dy) > STILL_EPSILON) patientMovingTicks++;
                }
            }
            for (int i = 0; i < members.length; i++) {
                long unit = sim.resolveUnit(members[i]);
                if (unit == 0L) continue;
                lastX[i] = sim.world().x(unit);
                lastY[i] = sim.world().y(unit);
            }
        }

        private int indexOf(long unit) {
            for (int i = 0; i < members.length; i++) if (members[i] == unit) return i;
            return -1;
        }

        Map<String, Number> metrics(SquadMedic medic) {
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("arrivedTick", arrivedTick);
            metrics.put("endCentroidX", Math.round(lastCentroidX * 10f) / 10f);
            metrics.put("squadHpStart", Math.round(startSquadHp * 10f) / 10f);
            metrics.put("squadHpEnd", Math.round(lastSquadHp * 10f) / 10f);
            metrics.put("medicShots", medicShots);
            metrics.put("shotsWhileTreating", shotsWhileTreating);
            metrics.put("treatingTicks", treatingTicks);
            metrics.put("patientMovingTicks", patientMovingTicks);
            metrics.put("planLessTicks", planLessTicks);
            metrics.put("medicReplacements", medicReplacements);
            metrics.put("hpLost", Math.round(hpLost * 10f) / 10f);
            metrics.put("deaths", deaths);
            metrics.put("defenderShots", defenderShots);
            metrics.put("friendlyFire", Math.round(lastFriendly * 10f) / 10f);
            metrics.put("friendlyFireAfterArrival", Math.round(friendlyAfterArrival * 10f) / 10f);
            for (SquadMedic.Phase phase : SquadMedic.Phase.values()) {
                metrics.put("friendlyFireWhile" + phase.name().charAt(0)
                                + phase.name().substring(1).toLowerCase(Locale.ROOT),
                        Math.round(friendlyByPhase[phase.ordinal()] * 10f) / 10f);
            }
            if (medic != null) {
                metrics.put("treatmentsStarted", medic.started());
                metrics.put("treatmentsCompleted", medic.completed());
                metrics.put("treatmentsInterrupted", medic.interrupted());
                metrics.put("healthRestored", Math.round(medic.healthRestored() * 10f) / 10f);
                metrics.put("armorRestored", Math.round(medic.armorRestored() * 10f) / 10f);
                metrics.put("armorReplated", medic.armorReplated());
                metrics.put("treatmentsExhausted", medic.exhausted());
                metrics.put("energyLeft", Math.round(medic.energy() * 10f) / 10f);
            }
            return metrics;
        }
    }
}
