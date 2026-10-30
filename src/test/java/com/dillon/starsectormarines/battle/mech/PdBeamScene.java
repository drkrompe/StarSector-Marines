package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
import com.dillon.starsectormarines.battle.weapon.RotaryFeedDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The PD lasers' held beams against men (`mechs-nouns.md`, Beam weapons): <b>does an autonomous beam fire at a
 * rifle section, and does it leave its reservoir headroom rather than ride it to its own hold?</b>
 *
 * <p>One planted Bulwark with the same gun on both arms and nothing on its shoulders, a twelve-strong rifle section
 * fifteen cells east of it, four seeds a loop: the light PD laser, the PD laser, and the chaingun and AC-6 as the
 * guns they are read against. Run again under {@code -Dbattle.mech.pdBeam=false} for the pulsed-burst control.
 *
 * <p>Verdicts are mechanics only: a beam fires at men, and an autonomous beam's reservoir never passes its stop
 * share by more than the pulses both arms may lay in the tick they cross it. Who wins, how many die, how fast, and
 * how hot the frame ran are readings.
 */
public final class PdBeamScene implements BehaviorScene {

    static final int FRAME_EVERY_TICKS = 10;

    private static final int WIDTH = 64;
    private static final int HEIGHT = 25;
    private static final int WINDOW_TICKS = 1800;
    private static final int SEEDS = 4;
    private static final int MECH_X = 10;
    private static final int MECH_Y = 12;
    /** Fifteen cells off: inside every arm gun's reach. */
    private static final int SECTION_X = 25;
    private static final int SECTION_SIZE = 12;
    private static final String RIFLE = "weapon.field-rifle";
    private static final String MECH = "bulwark";
    private static final String SECTION = "rifles";

    @Override public String id() { return "pd-beam"; }

    @Override
    public String label() {
        return "PD beams: a held lance burns men and leaves the reservoir headroom";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(arm(frames, "light-pd-laser", MechWeaponComponent.LIGHT_PULSE_LASER));
        reports.add(arm(frames, "pd-laser", MechWeaponComponent.PD_LASER));
        reports.add(arm(frames, "chaingun", MechWeaponComponent.CHAINGUN));
        reports.add(arm(frames, "ac-6", MechWeaponComponent.AC_6));
        return reports;
    }

    private SceneReport arm(FrameSink frames, String loopId, MechWeaponComponent gun) {
        List<Trace> traces = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            traces.add(playOnce(seed == 1 ? frames : FrameSink.NONE, loopId, gun, seed));
        }
        return report(loopId, gun, traces);
    }

    private Trace playOnce(FrameSink frames, String loopId, MechWeaponComponent gun, long seed) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(seed);
        MechVariant variant = MechVariant.BULWARK;
        MechDeploymentSpec fit = new MechDeploymentSpec(variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD, gun, null, null);
        // A one-mech lance forms up a cell and a half west of its spawn cell, so ask two east.
        builder.squad(MECH).faction(Faction.MARINE).size(1).at(MECH_X + 2, MECH_Y)
                .mech(fit)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, MECH_X, MECH_Y))
                .done();
        builder.squad(SECTION).faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(SECTION_SIZE)
                .primary(RIFLE)
                .inFile(SECTION_X, MECH_Y - SECTION_SIZE / 2, 0, 1)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, SECTION_X, MECH_Y))
                .done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        Trace trace = new Trace(world.members(MECH)[0], world.members(SECTION));
        SceneRun run = SceneRun.of(world).observe(trace).frames(frames, loopId, FRAME_EVERY_TICKS, trace::caption);
        try {
            while (run.tick() < WINDOW_TICKS && !trace.over()) run.step();
            trace.menKilled = trace.men.length - trace.menAlive(sim);
        } finally {
            sim.close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    private static final class Trace implements TickObserver {
        final long mech;
        final long[] men;
        int ticks;
        int mechDeathTick = -1;
        int sectionBrokenTick = -1;
        int armRounds;
        int menKilled;
        float peakFlux;
        float fluxSum;
        int fluxTicks;
        int ventTicks;
        float capacity = MechFlux.CAPACITY;

        Trace(long mech, long[] men) {
            this.mech = mech;
            this.men = men;
        }

        boolean over() {
            return mechDeathTick >= 0 || sectionBrokenTick >= 0;
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
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId == mech && shot.weaponDef() != null) armRounds++;
            }
            if (mechDeathTick >= 0 || !sim.world().hasMechLoadout(mech)) return;
            MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
            capacity = loadout.flux.capacity();
            float flux = loadout.flux.value();
            peakFlux = Math.max(peakFlux, flux);
            fluxSum += flux;
            fluxTicks++;
            if (loadout.flux.venting()) ventTicks++;
        }

        String caption(int tick) {
            return String.format(Locale.ROOT, "pd beam  t=%.1fs  arm rounds %d  peak flux %.1f  vent ticks %d",
                    tick / 30f, armRounds, peakFlux, ventTicks);
        }
    }

    private SceneReport report(String loopId, MechWeaponComponent gun, List<Trace> traces) {
        int ticks = 0;
        int armRounds = 0;
        int mechDeaths = 0;
        int sectionsBroken = 0;
        int menKilled = 0;
        int ventTicks = 0;
        float breakSeconds = 0f;
        float peak = 0f;
        float fluxSum = 0f;
        int fluxTicks = 0;
        float capacity = MechFlux.CAPACITY;
        for (Trace t : traces) {
            ticks += t.ticks;
            armRounds += t.armRounds;
            if (t.mechDeathTick >= 0) mechDeaths++;
            if (t.sectionBrokenTick >= 0) {
                sectionsBroken++;
                breakSeconds += t.sectionBrokenTick / 30f;
            }
            menKilled += t.menKilled;
            ventTicks += t.ventTicks;
            peak = Math.max(peak, t.peakFlux);
            fluxSum += t.fluxSum;
            fluxTicks += t.fluxTicks;
            capacity = t.capacity;
        }
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("arm-guns-fire-at-men", armRounds > 0,
                armRounds + " arm rounds over " + traces.size() + " plays"));
        RotaryFeedDef feed = gun.rotary();
        if (feed != null && feed.beam()) {
            // Both arms may lay a pulse in the tick the reservoir crosses the stop share.
            float ceiling = RotaryFeedDef.BEAM_AI_STOP_SHARE * capacity + 2f * feed.fluxPerRound() + 0.01f;
            verdicts.add(Verdict.of("autonomous-beam-keeps-headroom", peak <= ceiling,
                    String.format(Locale.ROOT, "peak flux %.2f against a ceiling of %.2f", peak, ceiling)));
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("plays", traces.size());
        m.put("ticks", ticks);
        m.put("pdBeam", MechWeaponComponent.pdBeam() ? 1 : 0);
        m.put("menPerPlay", SECTION_SIZE);
        m.put("menKilled", menKilled);
        m.put("sectionsBroken", sectionsBroken);
        m.put("meanSecondsToBreak", sectionsBroken > 0 ? breakSeconds / sectionsBroken : -1f);
        m.put("mechDeaths", mechDeaths);
        m.put("armRounds", armRounds);
        m.put("peakFlux", peak);
        m.put("meanFlux", fluxTicks > 0 ? fluxSum / fluxTicks : 0f);
        m.put("ventTicks", ventTicks);
        return new SceneReport(id(), loopId, ticks, verdicts, m);
    }
}
