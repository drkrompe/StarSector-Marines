package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;

import java.util.List;

/**
 * One side's view of the Ruins vault objective ({@code ruins-expedition.md}, The Ruins mission family), frozen by
 * {@link RuinsObjectiveDisclosure}. The company's view is the vault, the approach to it, every lift point, the haul
 * and its escort, and the field caches it has found; the occupant's view is its vault and whether the company has
 * reached it. Cells it does not see are {@code -1}, lists it does not see are empty.
 *
 * @param phase         the objective's {@link ExtractionPayloadObjective.Phase} name to the company; {@code QUIET},
 *                      {@code ALARM}, {@code COMPLETE} or {@code FAILED} to the occupant
 * @param approachRoute the walk from the landing area to the vault as cell pairs, its first cell the landing area
 * @param destination   the index in {@code liftPoints} the haul is bound for, or {@code -1}
 * @param ruinsPhase    {@code APPROACH} or {@code DELVE} ({@link RuinsVaultObjective.RuinsPhase}); to the occupant
 *                      always {@code APPROACH}
 * @param pullOut       where the company pulls out from now, or {@code null} to the occupant
 * @param entrances     the interior's entrances, each with its apron, or empty to the occupant
 */
public record RuinsObjectiveFacts(
        String haulId,
        String haulName,
        String vaultName,
        String phase,
        int vaultCellX,
        int vaultCellY,
        int[] approachRoute,
        int haulCellX,
        int haulCellY,
        int guideCellX,
        int guideCellY,
        float breachProgress,
        float breachDuration,
        boolean vaultPowered,
        List<LiftPoint> liftPoints,
        int destination,
        boolean alarmActive,
        int alarmRaisedTick,
        int controllingSquadId,
        boolean escortPresent,
        boolean contested,
        boolean complete,
        boolean failed,
        ExtractionPayloadObjective.Failure failure,
        List<Cache> caches,
        String ruinsPhase,
        PullOut pullOut,
        List<Entrance> entrances) {

    /**
     * A lift point as the company sees it.
     *
     * @param routeCells the haul's route length from the vault to here
     * @param available  whether the boat may come down here now: the landing area always, a court once held
     */
    public record LiftPoint(String name, boolean landingArea, int cellX, int cellY, int routeCells,
                            boolean available) { }

    /**
     * Where the company pulls out from: the landing in the approach, the entry in the delve.
     *
     * @param liftPoint the index in {@code liftPoints}
     * @param held      whether the company holds it now
     */
    public record PullOut(String name, int liftPoint, int cellX, int cellY, int radius, boolean held) { }

    /**
     * An entrance of the interior as the company sees it: the map shows the megastructure's outline and its entrances
     * from the start, never what is inside.
     *
     * @param liftPoint    the index in {@code liftPoints}
     * @param cellX        its apron, where it is held
     * @param taken        whether the company has ever taken it
     * @param held         whether the company holds it now
     * @param entry        whether it is the entry, the first taken
     * @param holdProgress how far the side pressing the apron has got: the company while not held, the occupant while
     *                     held
     */
    public record Entrance(String name, int liftPoint, int cellX, int cellY, boolean taken, boolean held,
                           boolean entry, float holdProgress, float holdDuration) { }

    /** A field cache the company has found and not yet secured. */
    public record Cache(String id, int cellX, int cellY) { }

    public RuinsObjectiveFacts {
        approachRoute = approachRoute.clone();
        liftPoints = List.copyOf(liftPoints);
        caches = List.copyOf(caches);
        entrances = List.copyOf(entrances);
    }

    /** Whether the company has taken an entrance and is in the delve. */
    public boolean delve() { return "DELVE".equals(ruinsPhase); }

    @Override public int[] approachRoute() { return approachRoute.clone(); }

    public int approachCellCount() { return approachRoute.length / 2; }
    public int approachCellX(int index) { return approachRoute[index * 2]; }
    public int approachCellY(int index) { return approachRoute[index * 2 + 1]; }

    public boolean terminal() { return complete || failed; }

    /** The lift point the haul is bound for, or {@code null} when this view has none. */
    public LiftPoint boundLiftPoint() {
        return destination >= 0 && destination < liftPoints.size() ? liftPoints.get(destination) : null;
    }
}
