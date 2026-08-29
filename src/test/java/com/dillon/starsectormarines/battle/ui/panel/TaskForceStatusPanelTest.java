package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskForceStatusPanelTest {

    @Test
    void fortyFourSquadsCollapseIntoOneBoundedForceSnapshot() {
        List<Squad> squads = new ArrayList<>();
        for (int index = 0; index < 44; index++) {
            Squad squad = new Squad(index + 1, Faction.MARINE);
            squad.originalSize = 12;
            squad.aliveMembers = index < 2 ? 0 : index < 5 ? 6 : 12;
            squad.moraleBroken = index >= 2 && index < 5;
            squad.morale = squad.moraleBroken ? 0.1f : 0.8f;
            squad.alertLevel = switch (index % 3) {
                case 0 -> SquadAlertLevel.ENGAGED;
                case 1 -> SquadAlertLevel.SUSPICIOUS;
                default -> SquadAlertLevel.UNAWARE;
            };
            squads.add(squad);
        }
        Squad defender = new Squad(100, Faction.DEFENDER);
        defender.originalSize = 12;
        defender.aliveMembers = 12;
        squads.add(defender);
        squads.add(new Squad(101, Faction.MARINE));

        TaskForceStatusPanel.Snapshot snapshot =
                TaskForceStatusPanel.Snapshot.capture(squads);

        assertEquals(44, snapshot.committedSquads());
        assertEquals(39, snapshot.effectiveSquads());
        assertEquals(486, snapshot.aliveMarines());
        assertEquals(528, snapshot.landedMarines());
        assertEquals(14, snapshot.engagedSquads());
        assertEquals(14, snapshot.suspiciousSquads());
        assertEquals(14, snapshot.unawareSquads());
        assertEquals(3, snapshot.brokenSquads());
        assertEquals(31.5f, snapshot.totalMorale(), 0.001f);
        assertEquals(40.5f, snapshot.totalMoraleCap(), 0.001f);
    }

    @Test
    void noLandedMarineSquadKeepsThePlateClosed() {
        TaskForceStatusPanel.Snapshot snapshot =
                TaskForceStatusPanel.Snapshot.capture(List.of(
                        new Squad(1, Faction.MARINE),
                        new Squad(2, Faction.DEFENDER)));

        assertTrue(snapshot.empty());
        assertEquals(TaskForceStatusPanel.Snapshot.EMPTY, snapshot);
    }

    @Test
    void goapDiagnosticHasNoAllSquadOverview() {
        try (BattleSimulation simulation = new BattleSimulation(
                new NavigationGrid(8, 8), new CellTopology(8, 8))) {
            int squadId = simulation.mintSquad(Faction.MARINE, UnitType.MARINE);
            Squad squad = simulation.getSquad(squadId);
            squad.originalSize = 12;
            squad.aliveMembers = 12;
            Selection selection = new Selection();
            HighlightOverlay highlights = new HighlightOverlay();
            BattleUiContext context = new BattleUiContext() {
                @Override
                public BattleSimulation getSim() {
                    return simulation;
                }

                @Override
                public BattleFixture getBattleFixture() {
                    return null;
                }

                @Override
                public BattleCamera getCamera() {
                    return null;
                }

                @Override
                public BattleLayout getLayout() {
                    return null;
                }

                @Override
                public Selection getSelection() {
                    return selection;
                }

                @Override
                public HighlightOverlay getHighlights() {
                    return highlights;
                }
            };
            SquadPlanDebugPanel panel = new SquadPlanDebugPanel(context);

            panel.update(0f);

            assertFalse(panel.isVisible(),
                    "living squads alone must not open a GOAP overview");
        }
    }
}
