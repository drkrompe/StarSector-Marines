package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Bulwark against men: <b>does each missile warhead do the job it is fitted for?</b> The infantry
 * half of the acceptance for anti-armor and fragmentation racks ({@code mechs-nouns.md}, Missile warheads);
 * {@code armorMatchup} is the armor half.
 *
 * <p>One planted chaingun Bulwark and a twelve-strong rifle section fifteen cells east of it, played
 * with two SRM-15 fragmentation racks and again with two anti-armor racks. A third loop puts an
 * anti-armor Bulwark in front of a smaller screen with a breakthrough tank behind it: the mixed
 * fight, where the racks must turn the torso to the tank while they are loaded. Two more loops put a
 * factory Warden thirty cells from the section, beyond its cannon, with fragmentation and then
 * anti-armor LRM-5s: the artillery the Warden now ships with, against the rack it replaced.
 *
 * <p>Verdicts are mechanics only: an anti-armor rack never starts a salvo at a soft target, a
 * fragmentation rack does fire at men, and in the mixed loop the mech aims at the tank beyond
 * close-threat range. Who wins, how many die and how fast are readings.
 */
public final class MechVersusInfantryScene implements BehaviorScene {

    static final String FRAG_LOOP = "frag-open";
    static final String AT_LOOP = "at-open";
    static final String MIXED_LOOP = "at-mixed";
    static final String WARDEN_FRAG_LOOP = "warden-frag";
    static final String WARDEN_AT_LOOP = "warden-at";
    static final int FRAME_EVERY_TICKS = 10;

    private static final int WIDTH = 64;
    private static final int HEIGHT = 25;
    private static final int WINDOW_TICKS = 1800;
    private static final int SEEDS = 4;

    private static final int MECH_X = 10;
    private static final int MECH_Y = 12;
    /** Fifteen cells off: inside an SRM's eighteen, beyond the ten of close threat. */
    private static final int SECTION_X = 25;
    private static final int SECTION_SIZE = 12;
    private static final int SCREEN_X = 23;
    private static final int SCREEN_SIZE = 6;
    private static final int TANK_X = 26;
    /** Thirty cells off: beyond the heavy cannon's 26, where a Warden's LRMs lob. */
    private static final int ARTILLERY_X = 40;
    private static final String RIFLE = "weapon.field-rifle";

    private static final String MECH = "bulwark";
    private static final String SECTION = "rifles";

    @Override public String id() { return "mech-versus-infantry"; }

    @Override
    public String label() {
        return "Mech versus infantry: fragmentation racks fire at men, anti-armor racks hold for armor";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(arm(frames, FRAG_LOOP, MechVariant.BULWARK, MechWeaponComponent.SRM_15_FRAG, false));
        reports.add(arm(frames, AT_LOOP, MechVariant.BULWARK, MechWeaponComponent.SRM_15, false));
        reports.add(arm(frames, MIXED_LOOP, MechVariant.BULWARK, MechWeaponComponent.SRM_15, true));
        reports.add(arm(frames, WARDEN_FRAG_LOOP, MechVariant.WARDEN, MechWeaponComponent.LRM_5_FRAG, false));
        reports.add(arm(frames, WARDEN_AT_LOOP, MechVariant.WARDEN, MechWeaponComponent.LRM_5, false));
        return reports;
    }

    private SceneReport arm(FrameSink frames, String loopId, MechVariant variant, MechWeaponComponent rack,
                            boolean mixed) {
        List<Trace> traces = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            traces.add(playOnce(seed == 1 ? frames : FrameSink.NONE, loopId, variant, rack, mixed, seed));
        }
        return report(loopId, rack, mixed, traces);
    }

    private Trace playOnce(FrameSink frames, String loopId, MechVariant variant, MechWeaponComponent rack,
                           boolean mixed, long seed) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(seed);
        MechDeploymentSpec fit = new MechDeploymentSpec(variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD, variant.stockArmWeapon(), rack, rack);
        // A one-mech lance forms up a cell and a half west of its spawn cell, so ask two east.
        builder.squad(MECH).faction(Faction.MARINE).size(1).at(MECH_X + 2, MECH_Y)
                .mech(fit)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, MECH_X, MECH_Y))
                .done();
        int size = mixed ? SCREEN_SIZE : SECTION_SIZE;
        int x = mixed ? SCREEN_X : variant == MechVariant.WARDEN ? ARTILLERY_X : SECTION_X;
        builder.squad(SECTION).faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(size)
                .primary(RIFLE)
                .inFile(x, MECH_Y - size / 2, 0, 1)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, x, MECH_Y))
                .done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long tank = 0L;
        if (mixed) {
            float tx = TANK_X + 0.5f;
            float ty = MECH_Y + 0.5f;
            tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER, VehicleMission.deployed(tx, ty));
            sim.convoy().body(tank).teleport(tx, ty, 180f);
        }
        Trace trace = new Trace(world.members(MECH)[0], world.members(SECTION), tank);
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, loopId, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && !trace.over(sim)) run.step();
            trace.lastMenKilled = trace.men.length - trace.menAlive(sim);
        } finally {
            sim.close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Trace implements TickObserver {
        final long mech;
        final long[] men;
        final long tank;
        int ticks;
        int mechDeathTick = -1;
        int sectionBrokenTick = -1;
        int tankDeathTick = -1;
        /** Salvos an anti-armor rack began at a target that is not hardened. */
        int antiArmorSalvosAtSoft;
        /** Salvos a fragmentation rack began at a man. */
        int fragSalvosAtMen;
        /** Ticks the torso held the tank while it stood beyond close-threat range, with a rack loaded. */
        int tankAimTicks;
        int armRounds;
        int rackRounds;
        int lastMenKilled;
        /** One per hardpoint, indexed as {@link MechLoadoutComponent#mounts()} is: by slot ordinal. */
        final boolean[] wasBursting = new boolean[MechMountSlot.values().length];

        Trace(long mech, long[] men, long tank) {
            this.mech = mech;
            this.men = men;
            this.tank = tank;
        }

        boolean over(BattleSimulation sim) {
            return mechDeathTick >= 0 || (sectionBrokenTick >= 0 && (tank == 0L || tankDeathTick >= 0));
        }

        int menAlive(BattleSimulation sim) {
            int alive = 0;
            for (long man : men) if (sim.getRoster().isAliveById(man)) alive++;
            return alive;
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (mechDeathTick < 0 && !sim.getRoster().isAliveById(mech)) mechDeathTick = tick;
            if (sectionBrokenTick < 0 && menAlive(sim) == 0) sectionBrokenTick = tick;
            if (tank != 0L && tankDeathTick < 0
                    && !(sim.convoy().isVehicle(tank) && sim.convoy().isPresent(tank))) tankDeathTick = tick;
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != mech || shot.weaponDef() == null) continue;
                String id = shot.weaponDef().id;
                if (WeaponRegistry.isMechSrm(id) || WeaponRegistry.isMechLrm(id)) rackRounds++;
                else armRounds++;
            }
            if (mechDeathTick >= 0 || !sim.world().hasMechLoadout(mech)) return;
            MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
            MechWeaponMount[] mounts = loadout.mounts();
            boolean rackLoaded = false;
            for (int i = 0; i < mounts.length; i++) {
                MechWeaponMount mount = mounts[i];
                boolean bursting = mount != null && mount.burstRemaining > 0;
                if (mount != null && mount.component.mountFamily == MechWeaponComponent.MountFamily.SHOULDER) {
                    if (mount.hasAmmo()) rackLoaded = true;
                    if (bursting && !wasBursting[i] && mount.burstTargetId != 0L) {
                        boolean hardened = sim.isHardenedTarget(mount.burstTargetId);
                        if (mount.component.antiArmorOnly() && !hardened) antiArmorSalvosAtSoft++;
                        if (!mount.component.antiArmorOnly() && !hardened) fragSalvosAtMen++;
                    }
                }
                wasBursting[i] = bursting;
            }
            if (tank != 0L && tankDeathTick < 0 && rackLoaded && sim.targetOf(mech) == tank
                    && distance(sim, mech, tank) > MechTargeting.CLOSE_THREAT_AGGRO_RANGE) {
                tankAimTicks++;
            }
        }

        String caption(int tick) {
            return String.format(Locale.ROOT, "mech vs infantry  t=%.1fs  arm rounds %d  rack rounds %d"
                            + "  |  AT salvos at men %d  frag salvos at men %d  tank-aim ticks %d",
                    tick / 30f, armRounds, rackRounds, antiArmorSalvosAtSoft, fragSalvosAtMen, tankAimTicks);
        }
    }

    private static float distance(BattleSimulation sim, long a, long b) {
        float dx = sim.world().x(a) - sim.world().x(b);
        float dy = sim.world().y(a) - sim.world().y(b);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---- verdicts ----

    private SceneReport report(String loopId, MechWeaponComponent rack, boolean mixed, List<Trace> traces) {
        int ticks = 0;
        int atSoft = 0;
        int fragMen = 0;
        int tankAim = 0;
        int armRounds = 0;
        int rackRounds = 0;
        int mechDeaths = 0;
        int sectionsBroken = 0;
        int tanksKilled = 0;
        int menKilled = 0;
        float breakSeconds = 0f;
        for (Trace t : traces) {
            ticks += t.ticks;
            atSoft += t.antiArmorSalvosAtSoft;
            fragMen += t.fragSalvosAtMen;
            tankAim += t.tankAimTicks;
            armRounds += t.armRounds;
            rackRounds += t.rackRounds;
            if (t.mechDeathTick >= 0) mechDeaths++;
            if (t.tankDeathTick >= 0) tanksKilled++;
            if (t.sectionBrokenTick >= 0) {
                sectionsBroken++;
                breakSeconds += t.sectionBrokenTick / 30f;
            }
            menKilled += t.lastMenKilled;
        }
        List<Verdict> verdicts = new ArrayList<>();
        if (rack.antiArmorOnly()) {
            verdicts.add(Verdict.of("anti-armor-holds-at-men", atSoft == 0,
                    atSoft + " anti-armor salvos begun at a soft target over " + traces.size() + " plays"));
        } else {
            // A fragmentation rack, or any rack under the single-warhead control.
            verdicts.add(Verdict.of("rack-fires-at-men", fragMen > 0,
                    fragMen + " rack salvos begun at men over " + traces.size() + " plays"));
        }
        if (mixed && rack.antiArmorOnly()) {
            verdicts.add(Verdict.of("aims-at-the-tank", tankAim > 0,
                    tankAim + " ticks the torso held the tank beyond close-threat range with a rack loaded"));
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("plays", traces.size());
        m.put("ticks", ticks);
        m.put("menPerPlay", mixed ? SCREEN_SIZE : SECTION_SIZE);
        m.put("menKilled", menKilled);
        m.put("sectionsBroken", sectionsBroken);
        m.put("meanSecondsToBreak", sectionsBroken > 0 ? breakSeconds / sectionsBroken : -1f);
        m.put("mechDeaths", mechDeaths);
        m.put("tanksKilled", tanksKilled);
        m.put("armRounds", armRounds);
        m.put("rackRounds", rackRounds);
        m.put("antiArmorSalvosAtSoft", atSoft);
        m.put("fragSalvosAtMen", fragMen);
        m.put("tankAimTicks", tankAim);
        return new SceneReport(id(), loopId, ticks, verdicts, m);
    }
}
