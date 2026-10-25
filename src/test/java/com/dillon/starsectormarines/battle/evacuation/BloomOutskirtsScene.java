package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Colony Breach bloom coming in from the outskirts (`colony-breach-nouns.md`, Eruption site, Budding): a fissure
 * open on the town's edge beside a landed fire team, and the colony's people further in toward the town's heart.
 * <ul>
 *   <li>{@code outskirts}: the fissure's pulses forage into the town after the colonists rather than throw themselves
 *       at the fire team beside it; what they bring home feeds it, and fed, it buds: a root digs out a block further
 *       in and a new bore opens at its end, nearer the heart.</li>
 * </ul>
 */
public final class BloomOutskirtsScene implements BehaviorScene {

    static final int W = 170, H = 100;
    static final int BORE_X = 20, BORE_Y = 50;
    static final int TEAM_X = 24, TEAM_Y = 80;
    static final int TOWN_X = 80, TOWN_Y = 50;
    static final int HEART_X = 150, HEART_Y = 50;
    static final int COLONISTS = 10;
    static final int TICKS = 6000;

    @Override public String id() { return "bloom-outskirts"; }

    @Override public String label() {
        return "Bloom from the outskirts: a fissure's pulses forage into the town past the landed company, and fed,"
                + " it buds a new bore further in, root first";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(run("outskirts", frames));
    }

    private SceneReport run(String loop, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261025L).missionCompletion(false);
        builder.squad("team").faction(Faction.MARINE).size(4).at(TEAM_X, TEAM_Y).primary("weapon.field-rifle")
                .stationary().done();
        try (BattleSimulation sim = builder.build().sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            sim.creepField();
            sim.configureSwarmFromBores(20261025L);
            List<Long> folk = new ArrayList<>();
            for (int i = 0; i < COLONISTS; i++) {
                folk.add(sim.spawn(new EntitySpec("Colonist " + (i + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                        TOWN_X + (i % 5) * 4, TOWN_Y - 6 + (i / 5) * 12).role(UnitRole.FLEE)));
            }
            List<int[]> places = new ArrayList<>();
            for (int x = 30; x < W - 10; x += 10) {
                for (int y = 20; y < H - 10; y += 15) places.add(new int[]{x, y, 0});
            }
            sim.swarmBores().budInward(places, HEART_X, HEART_Y, 14, 2);
            SwarmHarvest harvest = sim.swarmHarvest();
            int budTick = -1, openTick = -1, foragers = 0;
            float teamHpAtMinute = -1f;
            SwarmBore budded = null;
            for (int tick = 1; tick <= TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                if (budTick < 0 && !sim.swarmBores().buds().isEmpty()) budTick = tick;
                if (budded == null) {
                    for (SwarmBore b : sim.swarmBores().bores()) if (b != bore) budded = b;
                    if (budded != null) openTick = tick;
                }
                if (tick == 1800) teamHpAtMinute = teamHp(sim);
                if (tick % 15 == 0) {
                    frames.frame(loop, sim, tick, "bloom outskirts / tick " + tick + " / fed " + bore.biomassFed()
                            + " / " + sim.swarmBores().bores().size() + " bores");
                }
                if (openTick > 0 && tick > openTick + 300) break;
            }
            for (int i = 0; i < sim.liveUnitCount(); i++) if (harvest.isForager(sim.liveUnitAt(i))) foragers++;
            int lead = Math.round(SwarmBores.BUD_LEAD_SECONDS / BattleSimulation.TICK_DT);
            float homeDist = (float) Math.hypot(bore.x() - HEART_X, bore.y() - HEART_Y);
            float budDist = budded == null ? -1f : (float) Math.hypot(budded.x() - HEART_X, budded.y() - HEART_Y);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("its-pulses-forage-into-the-town", harvest.seized() >= 3,
                    harvest.seized() + " of the colony's people seized, " + foragers + " foragers alive at the end"));
            verdicts.add(Verdict.of("the-landed-team-meets-only-a-few", teamHpAtMinute >= 0.5f,
                    "the fire team kept " + Math.round(teamHpAtMinute * 100) + "% of its health through the first minute"));
            verdicts.add(Verdict.of("fed-it-buds-a-bore-further-in", budded != null && budDist < homeDist,
                    "fed " + bore.biomassFed() + "; new bore " + (budded == null ? "none" : "at " + budded.x() + ","
                            + budded.y() + ", " + Math.round(budDist) + " cells from the heart against "
                            + Math.round(homeDist))));
            verdicts.add(Verdict.of("its-root-comes-first", budTick > 0 && openTick - budTick >= lead,
                    "root began at tick " + budTick + ", bore opened at tick " + openTick + " (lead " + lead + ")"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("seized", harvest.seized());
            metrics.put("delivered", harvest.delivered());
            metrics.put("biomassFed", bore.biomassFed());
            metrics.put("budTick", budTick);
            metrics.put("openTick", openTick);
            metrics.put("teamHpAtMinute", teamHpAtMinute);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    /** The fire team's health as a share of its full health. */
    private static float teamHp(BattleSimulation sim) {
        float hp = 0f;
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) != Faction.MARINE) continue;
            hp += sim.world().hp(u) / Math.max(1e-3f, sim.world().maxHp(u));
            n++;
        }
        return hp / 4f;
    }
}
