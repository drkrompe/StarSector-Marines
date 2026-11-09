package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.RuinsDelveRow;
import com.dillon.starsectormarines.battle.command.objective.RuinsPullOut;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Pick {@code ruins-stir-row}: where the site's stir reads on the compact rail ({@code ruins-pressure.md}, Cues). One
 * moment, a company dug in at a deep stand as a push goes round it, drawn three ways: (a) the level and the warning
 * take the Delve row's short line and its bar, as built; (b) the level rides the Delve row's heading; (c) a site row
 * of its own. 1920x1080 at 100%, look preset. Opt-in; it judges nothing.
 */
public final class RuinsStirRowPicksSuite implements SnapshotSuite {

    @Override public String id() { return "ruins-stir-row-picks"; }

    @Override public String label() { return "Ruins pressure pick: where the stir reads on the rail"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            RuinsRailLookSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA, Fonts.ORBITRON_20);
            try (BattleSimulation sim = RuinsPushCueLookSuite.delve(context, RuinsPushCueLookSuite.SCAVENGERS_DEEP)) {
                // A company on the move in a stirring site, past its fresh stand's report so the row shows its
                // everyday line.
                RuinsRailLookSuite.advance(sim, 25f);
                if (sim.ruinsPressure().dugIn()) throw new IllegalStateException("dug in before the moving shot");
                sim.ruinsPressure().loud(RuinsPressure.STEP);
                RuinsRailLookSuite.advance(sim, 3f);
                float[] moving = RuinsPushCueLookSuite.middle(sim, List.of(), Faction.MARINE);
                String stirring = Strings.get(sim.ruinsPressure().level().key);
                out.add(RuinsRailLookSuite.shot(context, sim, "ruins-stir-row-a-moving", moving[0], moving[1], 90f,
                        UnaryOperator.identity()));
                out.add(RuinsRailLookSuite.shot(context, sim, "ruins-stir-row-b-moving", moving[0], moving[1], 90f,
                        rail -> inTheHeading(rail, stirring, glance(sim))));
                out.add(RuinsRailLookSuite.shot(context, sim, "ruins-stir-row-c-moving", moving[0], moving[1], 90f,
                        rail -> ownRow(rail, stirring, glance(sim))));
            }
            // A fresh battle: the company held at its stand past the stillness window as a push goes round it.
            try (BattleSimulation sim = RuinsPushCueLookSuite.delve(context, RuinsPushCueLookSuite.SCAVENGERS_DEEP)) {
                for (int t = 0; t < 10; t++) {
                    RuinsPushCueLookSuite.holdTheCompany(sim);
                    RuinsRailLookSuite.advance(sim, 5f);
                }
                sim.ruinsPressure().loud(RuinsPressure.STEP);
                RuinsPushCueLookSuite.holdTheCompany(sim);
                RuinsRailLookSuite.advance(sim, 4f);
                if (!sim.ruinsPressure().dugIn()) throw new IllegalStateException("the company is not dug in");
                float[] company = RuinsPushCueLookSuite.middle(sim, List.of(), Faction.MARINE);
                String level = Strings.get(sim.ruinsPressure().level().key);
                out.add(RuinsRailLookSuite.shot(context, sim, "ruins-stir-row-a-dug-in", company[0], company[1], 90f,
                        UnaryOperator.identity()));
                out.add(RuinsRailLookSuite.shot(context, sim, "ruins-stir-row-b-dug-in", company[0], company[1], 90f,
                        rail -> inTheHeading(rail, level, glance(sim))));
                out.add(RuinsRailLookSuite.shot(context, sim, "ruins-stir-row-c-dug-in", company[0], company[1], 90f,
                        rail -> ownRow(rail, level, glance(sim))));
            }
        }
        return out;
    }

    /** (b) The level rides the Delve row's heading; the line keeps the warning, the bar the stir. */
    static MissionObjectiveSnapshot inTheHeading(MissionObjectiveSnapshot rail, String level, String glance) {
        return fold(rail, delve -> List.of(new Item(delve.id(), delve.name(), delve.state(),
                delve.phase().replace("DELVE", "DELVE / " + level.toUpperCase(Locale.ROOT)), delve.steps(),
                delve.instruction(), delve.blocker().isEmpty() ? glance : delve.blocker(), delve.progress(),
                delve.blocker(), delve.markers(),
                delve.focusMarkerId(), delve.optional())));
    }

    /** (c) A site row of its own under the Delve row: the level its heading, the bar and the warning its own. */
    static MissionObjectiveSnapshot ownRow(MissionObjectiveSnapshot rail, String level, String glance) {
        return fold(rail, delve -> List.of(new Item(delve.id(), delve.name(), delve.state(), delve.phase(),
                        delve.steps(), delve.instruction(), glance, -1f, "", delve.markers(),
                        delve.focusMarkerId(),
                        delve.optional()),
                new Item(delve.id() + "-stir", "Site", delve.state(), level.toUpperCase(Locale.ROOT), "", "",
                        delve.blocker().isEmpty() ? "Reserve thinned" : delve.blocker(), delve.progress(),
                        delve.blocker(), List.of(), "", false)));
    }

    /** The Delve row's everyday line, the company at a glance, as it reads without the stir. */
    private static String glance(BattleSimulation sim) {
        RuinsVaultObjective vault = RuinsPullOut.vault(sim);
        return RuinsDelveRow.glance(sim.getRuinsDelveWatch().picture(sim, vault));
    }

    private interface Merge {
        List<Item> apply(Item delve);
    }

    private static MissionObjectiveSnapshot fold(MissionObjectiveSnapshot rail, Merge merge) {
        List<Item> out = new ArrayList<>();
        for (Item item : rail.objectives()) {
            if (item.id().endsWith("-delve")) out.addAll(merge.apply(item));
            else out.add(item);
        }
        return new MissionObjectiveSnapshot(out, rail.squads());
    }
}
