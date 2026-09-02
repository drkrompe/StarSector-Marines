package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The protected market's own troops, standing on the company's side of the line.
 *
 * <p>A defence is fought at somebody's market, and that somebody has a garrison.
 * It is {@link Faction#ALLY}: hostile to the landing force, friendly to the
 * player's marines, and never in the player's command pool — an ally that takes
 * orders is a player unit, so these squads plan under the garrison posture the
 * defender side already uses, pointed the other way.
 *
 * <p>Three decisions are made elsewhere and read here. What the troops carry
 * comes from the market faction's own {@link GroundRosterProfile}, resolved
 * through the bridge's single path; how many there are comes from
 * {@link AlliedGarrisonSize}; and which mission has any of them at all comes
 * from the mission's stated allied faction. This class only puts them on the
 * ground.
 *
 * <p>They are placed as a defensive line between where the company comes ashore
 * and where the raiders are, the same shape the opening operation's local
 * militia takes: a garrison meets a landing between it and the thing it landed
 * for, not scattered across the map and not standing on the beach.
 */
public final class AlliedGarrison {

    /** Members per squad — a fireteam, matching every other garrison here. */
    public static final int SQUAD_SIZE = 4;

    /** Command issuer these squads are claimed under. */
    public static final String ISSUER = "allied-garrison";

    /**
     * Cells from the company's arrival toward the enemy anchor. Mirrors the
     * opening operation's own line offset: far enough forward that the garrison
     * is a line rather than a reception committee, close enough that the two
     * friendly forces are one front.
     */
    private static final int LINE_OFFSET = 9;

    /** How far a post's squad will work off its own node. */
    private static final int PATROL_RADIUS = 4;

    /** What the garrison stands up for, in the command trace. */
    private static final String REASON = "hold the colony's line";

    private AlliedGarrison() {}

    /** The squads that actually went in. Empty when nothing could be placed. */
    public record Installed(List<Integer> squadIds) {

        public static final Installed NONE = new Installed(List.of());

        public Installed {
            squadIds = List.copyOf(squadIds);
        }

        public int squadCount() { return squadIds.size(); }
    }

    /**
     * Stands the garrison up on a battle that already has its map, its defenders
     * and its landing zones.
     *
     * @param squads how many fireteams to field, from {@link AlliedGarrisonSize};
     *               zero or less installs nothing
     * @param risk   the mission's risk, which selects the roster's quality bands
     * @param rng    the caller's own salted stream, so the garrison's kit does not
     *               shift the defenders' rolls
     */
    public static Installed install(BattleSimulation sim, MapResult map,
                                    GroundRosterProfile roster, int squads,
                                    RiskLevel risk, Random rng) {
        if (sim == null || map == null || roster == null || squads <= 0) {
            return Installed.NONE;
        }
        int[] anchor = lineAnchor(map);
        List<int[]> cells = BattleSetup.pickDefensiveCluster(
                map.grid, anchor[0], anchor[1], squads * SQUAD_SIZE);
        if (cells.isEmpty()) return Installed.NONE;

        UnitType type = roster.unitType(GroundRosterProfile.ForceTier.BULK);
        List<Integer> squadIds = new ArrayList<>();
        Squad squad = null;
        int members = 0;
        for (int[] cell : cells) {
            if (squad == null || members >= SQUAD_SIZE) {
                if (squad != null) squad.originalSize = members;
                members = 0;
                squad = openPost(sim, cell[0], cell[1], type);
                squadIds.add(squad.id);
            }
            EntitySpec unit = BattleSetup.makeDefender(
                            "ally-" + (squadIds.size() - 1) + "-" + members,
                            Faction.ALLY, type, cell[0], cell[1], risk, roster,
                            GroundRosterProfile.ForceTier.BULK, rng, null)
                    .role(UnitRole.GARRISON)
                    .squad(squad.id)
                    .home(cell[0], cell[1]);
            long id = sim.spawn(unit);
            if (squad.leaderId == 0L) squad.leaderId = id;
            members++;
        }
        if (squad != null) squad.originalSize = members;
        return new Installed(squadIds);
    }

    /** A post on the given cell, already claimed so no commander can re-task it. */
    private static Squad openPost(BattleSimulation sim, int x, int y, UnitType type) {
        Squad squad = sim.getSquad(sim.mintSquad(Faction.ALLY, type));
        squad.assignedNode = BattleSetup.openingDefenseNode(
                x, y, Faction.ALLY, sim.getGrid());
        squad.patrolRadius = PATROL_RADIUS;
        sim.assignSquadCommand(
                ObjectiveAssignment.holdNode(squad.id, squad.assignedNode),
                CommandAuthority.GARRISON, ISSUER, REASON);
        return squad;
    }

    /**
     * Where the line forms: out from the company's arrival toward the enemy
     * anchor, clamped into the map. Read off the map's own two anchors rather
     * than off a landing pad, so a battle whose pads were selected differently
     * still forms its line in the same place.
     */
    private static int[] lineAnchor(MapResult map) {
        int towardEnemyX = Integer.compare(map.defenderSpawnX, map.marineSpawnX);
        int towardEnemyY = Integer.compare(map.defenderSpawnY, map.marineSpawnY);
        return new int[]{
                clamp(map.marineSpawnX + towardEnemyX * LINE_OFFSET,
                        map.grid.getWidth() - 1),
                clamp(map.marineSpawnY + towardEnemyY * LINE_OFFSET,
                        map.grid.getHeight() - 1)};
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }
}
