package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.salvage.FieldSalvage;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * The perspective boundary for the Ruins family ({@code mission-command-nouns.md}, Perspective and disclosure). The
 * company sees the vault, the approach, every lift point and whether it is open, the haul and its escort, and the
 * field caches its own sight has found; never the occupant's posts, patrols or stash. The occupant sees its vault and
 * whether the company has reached it, as Extraction's defenders see their source: not the lift points, the haul's
 * cell or the company's escort.
 */
public final class RuinsObjectiveDisclosure {

    private RuinsObjectiveDisclosure() { }

    public static RuinsObjectiveFacts freeze(BattleView sim, Faction perspective) {
        RuinsVaultObjective vault = null;
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective found) {
                if (vault != null) throw new IllegalStateException("a ruins battle has one vault");
                vault = found;
            }
        }
        if (vault == null) throw new IllegalStateException("a ruins battle needs its vault objective");
        FieldSalvage salvage = sim.fieldSalvage();
        return facts(vault, perspective, salvage != null ? salvage.caches() : List.of());
    }

    /** The facts one side sees of this vault, given the battle's field caches. */
    static RuinsObjectiveFacts facts(RuinsVaultObjective vault, Faction perspective, List<SalvageCache> caches) {
        if (perspective == Faction.MARINE) return company(vault, caches);
        if (perspective == Faction.DEFENDER) return occupant(vault);
        throw new IllegalArgumentException("ruins disclosure requires a combat perspective");
    }

    private static RuinsObjectiveFacts company(RuinsVaultObjective vault, List<SalvageCache> caches) {
        List<RuinsObjectiveFacts.LiftPoint> lifts = new ArrayList<>();
        List<RuinsObjectiveFacts.Entrance> entrances = new ArrayList<>();
        int[] approach = null;
        for (int i = 0; i < vault.liftPoints().size(); i++) {
            RuinsVaultObjective.LiftPoint point = vault.liftPoints().get(i);
            lifts.add(new RuinsObjectiveFacts.LiftPoint(point.name(), point.landingArea(),
                    vault.liftPointCellX(i), vault.liftPointCellY(i), point.route().length / 2,
                    vault.liftPointAvailable(i)));
            if (point.landingArea()) approach = reversed(point.route());
            if (point.entrance()) {
                entrances.add(new RuinsObjectiveFacts.Entrance(point.name(), i, vault.liftPointCellX(i),
                        vault.liftPointCellY(i), vault.entranceTaken(i), vault.liftPointAvailable(i),
                        vault.entry() == i, vault.apronHoldProgress(i), RuinsVaultObjective.COURT_HOLD_SECONDS));
            }
        }
        RuinsVaultObjective.PullOutPoint pullOut = vault.pullOutPoint();
        List<RuinsObjectiveFacts.Cache> found = new ArrayList<>();
        for (SalvageCache cache : caches) {
            if (cache.discovered() && !cache.secured()) {
                found.add(new RuinsObjectiveFacts.Cache(cache.id, cache.cellX, cache.cellY));
            }
        }
        return new RuinsObjectiveFacts(vault.payloadId(), vault.payloadName(), vault.sourceName(),
                vault.extractionPhase().name(), vault.sourceCellX(), vault.sourceCellY(), approach,
                vault.payloadCellX(), vault.payloadCellY(), vault.corridorGuideCellX(), vault.corridorGuideCellY(),
                vault.breachProgress(), vault.breachDuration(), vault.vaultPowered(), lifts, vault.destination(),
                vault.alarmActive(), vault.alarmRaisedTick(), vault.controllingSquadId(), vault.escortPresent(),
                vault.contested(), vault.isComplete(), vault.isFailed(), vault.failureReason(), found,
                vault.ruinsPhase().name(), new RuinsObjectiveFacts.PullOut(pullOut.name(), pullOut.liftPoint(),
                pullOut.cellX(), pullOut.cellY(), pullOut.radius(), pullOut.held()), entrances);
    }

    private static RuinsObjectiveFacts occupant(RuinsVaultObjective vault) {
        String phase = vault.isComplete() ? "COMPLETE" : vault.isFailed() ? "FAILED"
                : vault.alarmActive() ? "ALARM" : "QUIET";
        return new RuinsObjectiveFacts(vault.payloadId(), vault.payloadName(), vault.sourceName(), phase,
                vault.sourceCellX(), vault.sourceCellY(), new int[0], -1, -1, -1, -1, 0f, 0f, false,
                List.of(), -1, vault.alarmActive(), vault.alarmRaisedTick(), -1, false, false,
                vault.isComplete(), vault.isFailed(),
                vault.isFailed() ? vault.failureReason() : ExtractionPayloadObjective.Failure.NONE, List.of(),
                RuinsVaultObjective.RuinsPhase.APPROACH.name(), null, List.of());
    }

    private static int[] reversed(int[] route) {
        int[] out = new int[route.length];
        int cells = route.length / 2;
        for (int i = 0; i < cells; i++) {
            out[i * 2] = route[(cells - 1 - i) * 2];
            out[i * 2 + 1] = route[(cells - 1 - i) * 2 + 1];
        }
        return out;
    }
}
