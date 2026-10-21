package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A lone bore on open ground whose creep carries its umbrella outward
 * ({@link CreepOutposts}): spore colonies rooting on the creep, spaced by the
 * grade's reach. One loop per case:
 * <ul>
 *   <li>{@code fissure}: an unfed bore, which grows to bore tier inside the run and may then hold two.</li>
 *   <li>{@code maw}: a bore fed to maw, which may hold four.</li>
 *   <li>{@code collapsed}: the maw bore destroyed once its outposts stand; they wither with it.</li>
 * </ul>
 * Verdicts are the rule's mechanics: an outpost roots on living creep, keeps
 * its spacing from every bore and colony standing when it roots, never exceeds
 * its bore's cap, and dies with its bore. When the outposts root and how far
 * out they stand are metrics.
 */
public final class CreepOutpostScene implements BehaviorScene {

    static final int SIZE = 170;
    static final int CENTRE = SIZE / 2;
    private static final int TICKS = 6600;
    private static final int KILL_TICK = 6000;
    private static final int FRAME_EVERY = 150;

    @Override public String id() { return "creep-outposts"; }

    @Override public String label() {
        return "A bore's creep seeds spore colonies away from the mouth, spaced by their reach; they wither with the bore";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(
                playOne("fissure", 0f, false, frames),
                playOne("maw", 9f, false, frames),
                playOne("collapsed", 9f, true, frames));
    }

    private SceneReport playOne(String loop, float biomass, boolean kill, FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(SIZE, SIZE).seed(20261021L).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, CENTRE, CENTRE);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            if (biomass > 0f) sim.swarmBores().feed(sim, bore, biomass);
            Watch watch = new Watch(bore, kill);
            SceneRun.of(world).observe(watch)
                    .frames(frames, loop, FRAME_EVERY, tick -> loop + " / " + bore.tier() + " bore / "
                            + watch.living(sim) + " outposts / tick " + tick)
                    .run(kill ? TICKS : KILL_TICK);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("outposts-root-on-living-creep", watch.offCreep == 0,
                    watch.offCreep + " of " + watch.rooted + " outposts rooted off living creep"));
            verdicts.add(Verdict.of("outposts-keep-their-spacing", watch.crowded == 0,
                    watch.crowded + " of " + watch.rooted + " outposts rooted nearer than "
                            + String.format("%.1f", watch.spacing) + " cells to a standing bore or colony"));
            verdicts.add(Verdict.of("never-over-the-cap", watch.mostLiving <= CreepOutposts.capFor(bore.tier()),
                    watch.mostLiving + " living at most against a cap of " + CreepOutposts.capFor(bore.tier())));
            if (kill) {
                boolean withered = watch.livingAfterKill == 0;
                verdicts.add(Verdict.of("outposts-wither-with-the-bore", watch.rootedBeforeKill == 0 || withered,
                        watch.rootedBeforeKill == 0 ? "no outpost stood when the bore fell; nothing to wither"
                                : watch.livingAfterKill + " of " + watch.rootedBeforeKill + " still standing a second after"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("outposts", watch.rooted);
            metrics.put("firstOutpostTick", watch.firstTick);
            metrics.put("farthestCells", watch.farthest);
            metrics.put("spacingCells", watch.spacing);
            return new SceneReport(id(), loop, kill ? TICKS : KILL_TICK, verdicts, metrics);
        }
    }

    private static final class Watch implements TickObserver {
        private final SwarmBore bore;
        private final boolean kill;
        int seen;
        int rooted;
        int offCreep;
        int crowded;
        int mostLiving;
        int firstTick = -1;
        float farthest;
        float spacing;
        int rootedBeforeKill;
        int livingAfterKill = -1;

        Watch(SwarmBore bore, boolean kill) {
            this.bore = bore;
            this.kill = kill;
        }

        int living(BattleSimulation sim) {
            int n = 0;
            for (long id : bore.outposts()) if (sim.resolveUnit(id) != 0L) n++;
            return n;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            List<Long> outposts = bore.outposts();
            for (; seen < outposts.size(); seen++) {
                long id = outposts.get(seen);
                rooted++;
                if (firstTick < 0) firstTick = tick;
                spacing = CreepOutposts.spacingFor(bore.tier());
                int x = sim.world().cellX(id), y = sim.world().cellY(id);
                CreepField field = sim.creepFieldIfPresent();
                if (field == null || !field.isCreeped(x, y)) offCreep++;
                float ox = sim.world().x(id), oy = sim.world().y(id);
                farthest = Math.max(farthest, (float) Math.hypot(ox - (bore.x() + 0.5f), oy - (bore.y() + 0.5f)));
                // Half a cell of slack: an anchor stands at its cell centre, the rule measures cell centres.
                if (nearestAnchor(sim, id, ox, oy) < spacing - 0.5f) crowded++;
            }
            mostLiving = Math.max(mostLiving, living(sim));
            if (kill && tick == KILL_TICK) {
                rootedBeforeKill = living(sim);
                sim.applyDamage(bore.entityId(), 1_000_000f, 100f);
            }
            if (kill && tick == KILL_TICK + 30) livingAfterKill = living(sim);
        }

        private float nearestAnchor(BattleSimulation sim, long self, float x, float y) {
            float best = (float) Math.hypot(x - (bore.x() + 0.5f), y - (bore.y() + 0.5f));
            List<Long> others = new ArrayList<>(bore.colonies());
            others.addAll(bore.outposts());
            for (long other : others) {
                if (other == self || sim.resolveUnit(other) == 0L) continue;
                best = Math.min(best, (float) Math.hypot(x - sim.world().x(other), y - sim.world().y(other)));
            }
            return best;
        }
    }
}
