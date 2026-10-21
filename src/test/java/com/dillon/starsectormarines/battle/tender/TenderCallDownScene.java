package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.power.TenderCallDown;
import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tender called down onto the ground a company means to hold: the player spends the power on a clear patch, the
 * hull comes in over the battle, lands and grounds, the existing yard construction appears for it, a Vulcan is laid
 * and built, and with the tender's own drones recalled the Vulcan alone is what meets the runners sent at it.
 */
public final class TenderCallDownScene implements BehaviorScene {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 80;
    private static final int TICKS = 4800;
    private static final int SITE_X = 60;
    private static final int SITE_Y = 40;
    private static final int RAIDERS = 3;
    private static final int RAID_X = 118;

    @Override public String id() { return "tender-call-down"; }

    @Override public String label() {
        return "Call-down tender: the power lands a committed tender on chosen ground, it grounds and registers yard "
                + "construction, a Vulcan is built beside it, and the Vulcan alone meets the runners sent at it";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld world = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)
                .squad("raid").faction(Faction.DEFENDER).size(RAIDERS).at(RAID_X, 38).primary("weapon.smg").done()
                .unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 135, 75)
                .unit("spotter", Faction.MARINE, UnitType.MARINE, 52, 40)
                .build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261021L);
        CommandPowerService powers = sim.getCommandPowerService();
        powers.register(new TenderCallDown(List.of(ShipSupportSource.stockFor(Tender.HULL_ID))));
        Trace trace = new Trace(world);
        SceneRun.of(world).observe(trace)
                .frames(frames, "lift-hardpoint", 15, tick -> "call-down / tick " + tick)
                .run(TICKS);
        return List.of(report(trace));
    }

    private SceneReport report(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-tender-is-over-the-battle-before-it-lands",
                t.flyingSeen >= 0 && t.landedTick > t.flyingSeen,
                "hull first seen flying at tick " + t.flyingSeen + ", down at tick " + t.landedTick));
        verdicts.add(Verdict.of("it-lands-where-chosen-and-grounds",
                t.tender != null && t.tender.grounded() && t.tender.ship().berthed() && t.siteError <= 1.5f,
                "grounded " + (t.tender != null && t.tender.grounded()) + ", " + String.format("%.2f", t.siteError)
                        + " cells from the chosen cell"));
        verdicts.add(Verdict.of("tender-landed-is-announced-once", t.beats == 1 && t.beatTick == t.landedTick,
                t.beats + " tender_landed beat(s) at tick " + t.beatTick + ", down at " + t.landedTick));
        verdicts.add(Verdict.of("yard-construction-is-registered-for-it", t.yardForTender,
                "yard-construction bound to the landed tender: " + t.yardForTender));
        verdicts.add(Verdict.of("the-vulcan-is-built", t.turretTick >= 0 && t.order != null
                        && t.order.phase() == ConstructionOrder.Phase.COMPLETE,
                "order " + (t.order == null ? "never laid" : t.order.phase()) + ", turret standing at tick " + t.turretTick));
        verdicts.add(Verdict.of("the-vulcan-hurts-the-runners-with-the-screen-recalled",
                t.firstRaiderHurt > t.turretTick && t.turretTick >= 0 && t.defendersOutAtRelease == 0,
                "first raider hurt at tick " + t.firstRaiderHurt + " after the turret at tick " + t.turretTick
                        + ", " + t.defendersOutAtRelease + " defence drones out"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("landedTick", t.landedTick);
        metrics.put("turretTick", t.turretTick);
        metrics.put("firstRaiderHurtTick", t.firstRaiderHurt);
        metrics.put("raidersAlive", t.raidersAlive);
        return new SceneReport(id(), "lift-hardpoint", TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        Tender tender;
        ConstructionOrder order;
        int flyingSeen = -1;
        int landedTick = -1;
        int beats;
        int beatTick = -1;
        float siteError = Float.MAX_VALUE;
        boolean yardForTender;
        int turretTick = -1;
        int firstRaiderHurt = -1;
        int defendersOutAtRelease = -1;
        int raidersAlive = RAIDERS;
        private boolean released;
        private boolean recalled;
        private boolean called;

        Trace(SceneWorld world) { this.world = world; }

        @Override public void observe(BattleSimulation sim, int tick) {
            // The call needs ground the company can see: a spotter stands by the site and the call goes in once it does.
            if (!called && sim.playerSeesCell(SITE_X, SITE_Y)) {
                called = true;
                sim.getCommandPowerService().requestActivation(TenderCallDown.ID, SITE_X, SITE_Y);
            }
            if (flyingSeen < 0) for (Warship ship : sim.warships().ships()) if (!ship.berthed()) flyingSeen = tick;
            if (tender == null && !sim.tenders().tenders().isEmpty()) {
                tender = sim.tenders().tenders().get(0);
                landedTick = tick;
                siteError = (float) Math.hypot(tender.ship().centerX() - (SITE_X + 0.5f),
                        tender.ship().centerY() - (SITE_Y + 0.5f));
                for (StoryBeatLog.Event e : sim.getStoryBeats()) {
                    if (e.id().equals(TenderLanding.BEAT)) { beats++; beatTick = e.tick(); }
                }
                for (var power : sim.getCommandPowerService().getAvailablePowers()) {
                    if (power instanceof YardConstruction yard && yard.source() == tender) yardForTender = true;
                }
                // Lay the Vulcan through the power the player would use, in front of the bay.
                sim.getCommandPowerService().requestActivation("yard-construction",
                        (int) tender.bayX() + 6, (int) tender.bayY());
            }
            if (tender != null && order == null && !tender.orders().isEmpty()) order = tender.orders().get(0);
            UnitRosterService roster = sim.getRoster();
            boolean turret = false;
            long[] dense = roster.denseArray();
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                long u = dense[i];
                if (roster.identity().type(u).isTurret() && roster.identity().faction(u) == Faction.MARINE) turret = true;
            }
            if (turret && turretTick < 0) turretTick = tick;
            if (turret && !recalled) {
                recalled = true;
                tender.defenceOut(false);
            }
            // The runners are sent only once the screen is home, so the turret is the only thing that can meet them.
            if (recalled && !released && tender.defendersOut() == 0) {
                released = true;
                defendersOutAtRelease = tender.defendersOut();
                world.squad("raid").assignedObjective =
                        ObjectiveAssignment.attackMove(world.squadId("raid"), (int) tender.bayX() + 6, (int) tender.bayY());
            }
            if (released) {
                int alive = 0;
                for (long u : world.members("raid")) {
                    boolean live = roster.isAliveById(u);
                    if (live) alive++;
                    if (firstRaiderHurt < 0 && (!live || sim.world().hp(u) < sim.world().maxHp(u))) firstRaiderHurt = tick;
                }
                raidersAlive = alive;
            }
        }
    }
}
