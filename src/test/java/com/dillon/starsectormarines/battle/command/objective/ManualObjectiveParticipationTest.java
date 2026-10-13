package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.control.DirectControlSession;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Objective owners read one real controlled body; no mission generator or simulation scheduler. */
class ManualObjectiveParticipationTest {
    @ParameterizedTest @EnumSource(value = UnitType.class, names = {"MARINE", "HEAVY_MECH"})
    void loneControlledBodyCapturesAndStillRespectsContestingDefenders(UnitType type) {
        Fixture f = new Fixture(type);
        assertTrue(f.session.enter(f.player));
        var compounds = new CompoundService();
        var node = new TacticalNode(TacticalNode.Kind.BARRACKS, 10, 10, 8, 8, 12, 12,
                Faction.DEFENDER, 75, 0);
        var record = compounds.register(node);
        var capture = new CompoundCaptureSystem();
        long guard = f.spawn(Faction.DEFENDER, UnitType.MILITIA, 10, 11);
        for (int i = 0; i < 15; i++) capture.tick(1f, f.battle, compounds);
        assertEquals(CompoundService.CompoundState.CONTESTED, record.state);
        assertEquals(0f, record.captureProgress);
        f.roster.world().setCellPos(guard, 20, 20);
        for (int i = 0; i <= CompoundService.MARINE_HOLD_TIME; i++) capture.tick(1f, f.battle, compounds);
        assertEquals(CompoundService.CompoundState.MARINE_HELD, record.state);
        assertEquals(f.player, f.session.activeUnitId());
    }

    @ParameterizedTest @EnumSource(value = UnitType.class, names = {"MARINE", "HEAVY_MECH"})
    void loneControlledBodyCanRecoverAndCarryRaidToCompletion(UnitType type) {
        Fixture f = new Fixture(type);
        assertTrue(f.session.enter(f.player));
        var raid = new RaidObjective("records", "Records", 10, 10, f.zones.zoneIdAt(10, 10), 20, 20, .1f);
        for (int i = 0; i < 4; i++) raid.tick(f.battle);
        assertTrue(raid.targetSecured());
        assertFalse(raid.isComplete());
        f.roster.world().setCellPos(f.player, 20, 20);
        raid.tick(f.battle);
        assertTrue(raid.isComplete());
        assertEquals(f.player, f.session.activeUnitId());
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(24, 24);
        final ZoneGraph zones = new ZoneGraph(grid);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(24, 24), null);
        final List<Long> units = new ArrayList<>();
        final BattleControl battle;
        final DirectControlSession session;
        final long player;

        Fixture(UnitType type) {
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
            zones.rebuild();
            roster.setNavigationGrid(grid);
            battle = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> zones;
                        case "getSimTickIndex" -> 0;
                        case "world" -> roster.world();
                        case "identity" -> roster.identity();
                        case "movement" -> roster.movement();
                        case "liveUnitCount" -> units.size();
                        case "liveUnitAt" -> units.get((int) args[0]);
                        case "physicalRadius" -> roster.radius((long) args[0]);
                        case "getShelvedSquadDirective" -> null;
                        case "squadOf" -> roster.getSquad(roster.squad().squadId((long) args[0]));
                        case "squad" -> roster.squad();
                        case "resolveUnit" -> roster.isAliveById((long) args[0]) ? args[0] : 0L;
                        case "squadMemberCount" -> roster.squadMemberCount((int) args[0]);
                        case "squadMemberAt" -> roster.squadMemberArray((int) args[0])[(int) args[1]];
                        case "isRiding" -> roster.isRiding((long) args[0]);
                        case "cancelMechMoveOrder" -> null;
                        case "clearPath" -> {
                            roster.movement().setPathRef((long) args[0], new int[0]);
                            roster.movement().setPathIdx((long) args[0], 0);
                            yield null;
                        }
                        default -> throw new AssertionError(method.getName());
                    });
            session = new DirectControlSession(battle, roster, id -> false, () -> false);
            player = spawn(Faction.MARINE, type, 10, 10);
        }

        long spawn(Faction faction, UnitType type, int x, int y) {
            int squadId = roster.mintSquad(faction, type);
            EntitySpec spec = new EntitySpec("Unit", faction, type, x, y).squad(squadId);
            if (type.isMech()) spec = MechVariant.BULWARK.applyTo(spec);
            long id = roster.spawn(spec);
            if (type.isMech()) roster.world().attachMechLoadout(id, MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
            var squad = roster.getSquad(squadId);
            squad.leaderId = id;
            squad.aliveMembers = 1;
            units.add(id);
            return id;
        }
    }
}
