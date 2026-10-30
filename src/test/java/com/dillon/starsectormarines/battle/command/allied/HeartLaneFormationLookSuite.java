package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * How a heart lane's militia and armour go forward together (`heart-allied-commander.md`), one look recording each
 * on real generated ground, toward a swarm pack ahead: {@code lane-infantry-screen}, three militia teams screening a
 * tank pair and an APC; {@code lane-armour-screen}, the armour screening the militia; {@code lane-station}, the
 * control, every squad and hull sent straight to the rally point at its own pace (what the lanes do today). Play zoom
 * following the formation. Opt-in: a design question, not part of {@code all}.
 */
public final class HeartLaneFormationLookSuite implements SnapshotSuite {

    static final int W = 90, H = 44, TICKS = 900;
    static final int START = 8, RALLY = 78, AXIS = 22;
    static final int[] STILLS = {30, 70, 110, 140};

    enum Form { INFANTRY_SCREEN, ARMOUR_SCREEN, STATION }

    @Override public String id() { return "heart-lane-formation-look"; }

    @Override public String label() { return "Heart lane formations: infantry screen, armour screen, straight to the rally"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), shot("lane-infantry-screen", Form.INFANTRY_SCREEN)));
        out.addAll(LookReview.record(context.modRoot(), shot("lane-armour-screen", Form.ARMOUR_SCREEN)));
        out.addAll(LookReview.record(context.modRoot(), shot("lane-station", Form.STATION)));
        return out;
    }

    static LookReview.Shot shot(String name, Form form) {
        return LookReview.Shot.of(name, W, H, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            int x0 = placement.site().x0(), y0 = placement.site().y0();
            AlliedCommand allied = new AlliedCommand("lane-look");
            Random rng = new Random(23L);
            ScreenedAdvance.Element infantry = new ScreenedAdvance.Element();
            ScreenedAdvance.Element armour = new ScreenedAdvance.Element();
            boolean armourLeads = form == Form.ARMOUR_SCREEN;
            int infantryX = x0 + (armourLeads ? START - 6 : START), armourX = x0 + (armourLeads ? START : START - 8);
            for (int t = 0; t < 3; t++) {
                int y = y0 + AXIS - 10 + t * 10;
                List<int[]> cells = new ArrayList<>();
                for (int m = 0; m < ColonyMilitia.POST_SIZE; m++) cells.add(new int[]{infantryX - m % 2, y + m / 2 - 1});
                infantry.squad(ColonyMilitia.seatTeam(sim, cells, "Militia " + (t + 1), RiskLevel.MEDIUM, rng));
            }
            long lead = allied.deploy(sim, VehicleType.BREAKTHROUGH_TANK, armourX, y0 + AXIS - 7, 0f);
            armour.hull(lead);
            armour.hull(allied.deploy(sim, VehicleType.BREAKTHROUGH_TANK, armourX, y0 + AXIS + 7, 0f));
            armour.hull(allied.deploy(sim, VehicleStockFit.CONVOY_ESCORT, armourX - 2, y0 + AXIS, 0f));
            placement.subject("armour", lead);
            placement.subject("militia", sim.getSquad(infantry.squads.get(1)).leaderId);
            for (int i = 0; i < 14; i++) {
                sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                        x0 + 60 + i % 3, y0 + AXIS - 6 + (i / 3) * 3).role(UnitRole.SWARM_PRESSURE));
            }
            sim.getNavigationMesh().rebuild();
            AdvanceRoute route = AdvanceRoute.straight(x0 + START, y0 + AXIS, x0 + RALLY, y0 + AXIS);
            if (form == Form.STATION) {
                sendStraight(sim, allied, route, infantry, armour);
                placement.beforeTick(allied::advance);
            } else {
                ScreenedAdvance advance = ScreenedAdvance.builder(name, "lane-look", allied, route)
                        .screen(armourLeads ? armour : infantry)
                        .main(armourLeads ? infantry : armour)
                        .startWhen(ScreenedAdvance.Trigger.atSeconds(1f))
                        .build();
                placement.beforeTick(s -> { advance.advance(s); allied.advance(s); });
            }
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "armour", "militia")), TICKS)
                .withStills(STILLS);
    }

    /** The control: every squad and hull to its rally slot at once, as station orders alone send them. */
    private static void sendStraight(BattleSimulation sim, AlliedCommand allied, AdvanceRoute route,
                                     ScreenedAdvance.Element infantry, ScreenedAdvance.Element armour) {
        int i = 0, n = infantry.size();
        for (int squad : infantry.squads) {
            int[] at = route.slot(route.length(), i++, n, ScreenedAdvance.DEFAULT_SPACING);
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, at[0], at[1]), CommandAuthority.GARRISON,
                    "lane-look", "straight to the rally point");
        }
        i = 0;
        n = armour.size();
        float behind = route.length() - ScreenedAdvance.DEFAULT_SCREEN_AHEAD;
        for (long hull : armour.hulls) {
            int[] at = route.slot(behind, i++, n, ScreenedAdvance.DEFAULT_SPACING);
            allied.station(sim, hull, at[0], at[1]);
        }
    }
}
