package com.dillon.starsectormarines.battle.warship;

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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Slice 3 of the frigate duel ({@code warship-behaviours.md}, ship against ship): missiles at hulls. A Wolf (H) holds its station thirty-eight cells
 * from its target, outside every gun's reach on either side and inside its Sabots'.
 *
 * <p>Loops: {@code sabot} (a Hound (P) held on station: the Sabots fly to their split range short of it and their
 * warheads are aimed at it when they split), {@code crossing} (the same Hound flying back and forth across the Wolf's
 * bow, so a warhead aimed where the hull was at launch would miss), {@code harpoon} (a Brawler firing Harpoons at the
 * Wolf, whose point defence lasers may shoot them down; the Brawler's heavy mauler reaches too) and {@code control}
 * (ship fire off: no missile at a hull). Verdicts are mechanics; hits, losses and interceptions are readings.
 */
public final class FrigateMissilesScene implements BehaviorScene {

    private static final int WIDTH = 110;
    private static final int HEIGHT = 68;
    private static final float WOLF_X = 16f;
    private static final float TARGET_X = 86f;
    private static final float MID_Y = 34f;
    private static final int TICKS = 1500;
    private static final int FRAME_EVERY = 3;
    /** A split's warheads are aimed at the hull when their centre lies this close to the hull's led centre. */
    private static final float AIMED_CELLS = 1.5f;

    @Override public String id() { return "frigate-missiles"; }

    @Override public String label() {
        return "Frigate missiles: a Wolf (H)'s Sabots split short of a Hound (P) and their warheads converge on it; "
                + "a Brawler's Harpoons against the Wolf's point defence";
    }

    @Override public int frameDelayMillis() { return 50; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("sabot", "crossing", "harpoon", "control")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim, Warship wolf, Warship target) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // One unarmed bystander a side keeps the battle ticking; the ships are the subject.
        builder.squad("west-ground").faction(Faction.MARINE).size(4).at(4, 62).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(4).at(106, 62).unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        ShipFireOrder order = loop.equals("control") ? ShipFireOrder.OFF : ShipFireOrder.ALL;
        Warship wolf = ship("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, WOLF_X, MID_Y, 0f, order);
        Warship target;
        if (loop.equals("harpoon")) {
            target = ship("brawler", "brawler_Elite", Faction.DEFENDER, TARGET_X, MID_Y, 180f, order);
        } else if (loop.equals("crossing")) {
            target = ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, TARGET_X, MID_Y - 16f,
                    180f, order);
            target.flyRoute(new float[][]{{TARGET_X, MID_Y - 16f}, {TARGET_X, MID_Y + 16f}}, true);
        } else {
            target = ship("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, TARGET_X, MID_Y, 180f,
                    order);
        }
        sim.addWarship(wolf);
        sim.addWarship(target);
        Trace trace = new Trace(wolf, target);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return new Run(trace, sim, wolf, target);
    }

    private static Warship ship(String hull, String variant, Faction side, float x, float y, float facing,
                                ShipFireOrder order) throws Exception {
        Warship ship = Warship.fit(hull, variant, side, x, y, facing);
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(order);
        // Held on station: the guns are out of reach on both sides, so only the missiles can fight.
        ship.duelHelm(false);
        return ship;
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        WarshipShipFire fire = run.sim().warships().shipFire();
        WarshipPointDefence pd = run.sim().warships().pointDefence();
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Integer> byWeapon = fire.roundsByWeapon();
        int accounted = fire.warheadsOnHull() + fire.warheadsStrayed() + fire.warheadsShotDown() + t.warheadsAlive;
        switch (loop) {
            case "sabot", "crossing" -> {
                verdicts.add(Verdict.of("sabots-launch-at-the-hull",
                        fire.missilesFired() > 0 && byWeapon.keySet().equals(Set.of("sabot")),
                        fire.missilesFired() + " missiles; rounds by weapon " + byWeapon));
                verdicts.add(Verdict.of("warheads-are-aimed-at-the-hull-when-they-split",
                        fire.releases() > 0 && fire.worstReleaseSpread() <= AIMED_CELLS,
                        String.format(Locale.ROOT, "%d warheads released in %d splits at a live hull; worst centre "
                                        + "%.2f cells from the hull's led centre", fire.warheadsReleased(),
                                fire.releases(), fire.worstReleaseSpread())));
                verdicts.add(Verdict.of("every-warhead-lands-on-a-hull-or-the-ground",
                        fire.warheadsOnHull() > 0 && accounted == fire.warheadsReleased(),
                        String.format(Locale.ROOT, "%d on hull, %d strayed to the ground, %d shot down, %d still "
                                        + "flying, of %d", fire.warheadsOnHull(), fire.warheadsStrayed(),
                                fire.warheadsShotDown(), t.warheadsAlive, fire.warheadsReleased())));
            }
            case "harpoon" -> {
                verdicts.add(Verdict.of("harpoons-launch-at-the-hull",
                        byWeapon.getOrDefault("harpoon_single", 0) > 0, "rounds by weapon " + byWeapon));
                verdicts.add(Verdict.of("point-defence-engages-missiles-aimed-at-its-hull",
                        pd.engagements() > 0,
                        String.format(Locale.ROOT, "the Wolf's point defence (the Brawler carries none): %d "
                                + "engagements, %d intercepts", pd.engagements(), pd.intercepts())));
            }
            default -> verdicts.add(Verdict.of("no-missile-without-ship-fire",
                    fire.missilesFired() == 0 && fire.roundsFired() == 0,
                    fire.missilesFired() + " missiles, " + fire.roundsFired() + " rounds at hulls"));
        }
        verdicts.add(Verdict.of("held-ships-stay-on-station", t.maxDrift < 1f || loop.equals("crossing"),
                String.format(Locale.ROOT, "Wolf furthest from station %.1f cells", t.maxDrift)));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("missilesFired", fire.missilesFired());
        metrics.put("missilesShotDown", fire.missilesShotDown());
        metrics.put("warheadsReleased", fire.warheadsReleased());
        metrics.put("warheadsOnHull", fire.warheadsOnHull());
        metrics.put("warheadsStrayed", fire.warheadsStrayed());
        metrics.put("warheadsShotDown", fire.warheadsShotDown());
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("roundsOnHull", fire.roundsOnHull());
        metrics.put("roundsStrayed", fire.roundsStrayed());
        metrics.put("pdEngagements", pd.engagements());
        metrics.put("pdIntercepts", pd.intercepts());
        metrics.put("wolfHullLost", Math.round(run.wolf().maxHitPoints - Math.max(0f, run.wolf().hitPoints())));
        metrics.put("targetHullLost", Math.round(run.target().maxHitPoints - Math.max(0f, run.target().hitPoints())));
        metrics.put("targetLost", run.target().destroyed() ? 1 : 0);
        metrics.put("wolfShieldHits", run.wolf().shieldHits());
        metrics.put("worstSplitSpreadCells", Math.round(fire.worstReleaseSpread() * 100f) / 100f);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Warship wolf;
        final Warship target;
        private final float homeX;
        private final float homeY;
        float maxDrift;
        int warheadsAlive;

        Trace(Warship wolf, Warship target) {
            this.wolf = wolf;
            this.target = target;
            this.homeX = wolf.x;
            this.homeY = wolf.y;
        }

        String caption(String loop, int tick) {
            return String.format(Locale.ROOT, "%s / tick %d%s%s", loop, tick, describe("Wolf", wolf),
                    describe(target.hullId, target));
        }

        private static String describe(String name, Warship ship) {
            if (ship.destroyed()) return " / " + name + " down";
            return String.format(Locale.ROOT, " / %s %.0f%% flux %.0f%%%s", name, ship.hullFraction() * 100f,
                    ship.fluxFraction() * 100f, ship.shieldUp() ? " shield" : "");
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (!wolf.destroyed()) maxDrift = Math.max(maxDrift, (float) Math.hypot(wolf.x - homeX, wolf.y - homeY));
            int alive = 0;
            for (WarheadStrike s : sim.warships().strikes()) if (s.hullTarget() != null && !s.intercepted) alive++;
            warheadsAlive = alive;
        }
    }
}
