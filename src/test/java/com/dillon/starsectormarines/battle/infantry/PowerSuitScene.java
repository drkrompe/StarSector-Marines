package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded exchange evidence for concentrated infantry strength. The same open
 * lane and opposition meet one regular squad, one XIV squad, and two regular
 * squads. A fourth loop gives the opposition penetrating marksman primaries.
 * Outcomes are measurements, never victory assertions or campaign balance proof.
 * All shooters have Service weapons and the same Regular/Steady profile, with
 * no integral systems, specials, or officer bonuses: this isolates the armor
 * package rather than compounding the existing experience/weapon progression.
 */
public final class PowerSuitScene implements BehaviorScene {
    private static final int TICKS = 1800;
    private static final int CADENCE = 60;
    private static final int SQUAD_SIZE = 12;

    @Override public String id() { return "power-suit"; }
    @Override public String label() {
        return "Power-suit infantry: fixed-kit exchange evidence, not mission balance";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        if (MarineArmorCatalogRegistry.installed() == null) {
            MarineArmorCatalogRegistry catalog = new MarineArmorCatalogRegistry();
            for (String file : MarineArmorCatalogRegistry.BUILTIN_CATALOGS) {
                catalog.ingest(new JSONObject(Files.readString(Path.of("mod").resolve(file))));
            }
            MarineArmorCatalogRegistry.install(catalog);
        }
        String property = UnitUpdateSystem.MINIMUM_PARALLEL_UNITS_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, Integer.toString(Integer.MAX_VALUE));
        try {
            return List.of(
                    exchange(frames, "one-regular", 1, "armor.line", WeaponRegistry.STARTER_PRIMARY_ID),
                    exchange(frames, "one-xiv", 1, "armor.heavy", WeaponRegistry.STARTER_PRIMARY_ID),
                    exchange(frames, "two-regular", 2, "armor.line", WeaponRegistry.STARTER_PRIMARY_ID),
                    exchange(frames, "xiv-vs-marksmen", 1, "armor.heavy", WeaponRegistry.DMR_ID));
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private SceneReport exchange(FrameSink frames, String loop, int friendlySquads,
                                 String armorId, String defenderWeapon) {
        SceneWorld world = SceneBuilder.openGround(72, 40).build();
        BattleSimulation sim = world.sim();
        List<Long> friendlies = new ArrayList<>();
        List<Long> defenders = new ArrayList<>();
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        MarineArmorCatalogDef armor = catalog.get(armorId);
        MarineArmorCatalogDef regular = catalog.get("armor.line");
        int zone = world.zoneAt(36, 20);
        for (int i = 0; i < friendlySquads; i++) {
            friendlies.addAll(spawn(sim, Faction.MARINE, armor, WeaponRegistry.STARTER_PRIMARY_ID,
                    23, friendlySquads == 1 ? 17 : 11 + i * 13, zone));
        }
        for (int i = 0; i < 2; i++) {
            defenders.addAll(spawn(sim, Faction.DEFENDER, regular, defenderWeapon,
                    40, 11 + i * 13, zone));
        }
        int friendlyShots = 0;
        int defenderShots = 0;
        int firstFriendlyLoss = -1;
        int clearedTick = -1;
        int wipedTick = -1;
        int ticks = 0;
        try {
            for (; ticks < TICKS; ticks++) {
                if (ticks % CADENCE == 0) {
                    frames.frame(loop, sim, ticks, caption(loop, sim, friendlies, defenders, ticks));
                }
                sim.advance(BattleSimulation.TICK_DT);
                frames.advanced(loop, sim, ticks);
                for (ShotEvent shot : sim.getShotsThisFrame()) {
                    if (shot.shooterFaction == Faction.MARINE) friendlyShots++;
                    if (shot.shooterFaction == Faction.DEFENDER) defenderShots++;
                }
                int alive = alive(sim, friendlies);
                int hostile = alive(sim, defenders);
                if (firstFriendlyLoss < 0 && alive < friendlies.size()) firstFriendlyLoss = ticks + 1;
                if (clearedTick < 0 && hostile == 0) clearedTick = ticks + 1;
                if (wipedTick < 0 && alive == 0) wipedTick = ticks + 1;
                if (alive == 0 || hostile == 0) { ticks++; break; }
            }
            frames.frame(loop, sim, ticks, caption(loop, sim, friendlies, defenders, ticks));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("friendlySquads", friendlySquads);
            metrics.put("friendlyStarting", friendlies.size());
            metrics.put("friendlySurvivors", alive(sim, friendlies));
            metrics.put("defenderStarting", defenders.size());
            metrics.put("defenderSurvivors", alive(sim, defenders));
            metrics.put("armorCapacityPerMarine", armor.armorCapacity());
            metrics.put("armorRating", armor.armorRating());
            metrics.put("friendlyShots", friendlyShots);
            metrics.put("defenderShots", defenderShots);
            metrics.put("firstFriendlyLossTick", firstFriendlyLoss);
            metrics.put("defendersClearedTick", clearedTick);
            metrics.put("friendliesWipedTick", wipedTick);
            metrics.put("friendlyRemainingArmor", remainingArmor(sim, friendlies));
            metrics.put("defenderPenetration", WeaponRegistry.require(defenderWeapon).penetration);
            List<Verdict> verdicts = List.of(
                    Verdict.of("both-sides-fired", friendlyShots > 0 && defenderShots > 0,
                            friendlyShots + " friendly shots; " + defenderShots + " defender shots"),
                    Verdict.of("casualties-observed", alive(sim, friendlies) < friendlies.size()
                                    || alive(sim, defenders) < defenders.size(),
                            "Outcomes are observations; a passed scene does not certify victory or two-squad equivalence."));
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        } finally {
            sim.close();
        }
    }

    private static List<Long> spawn(BattleSimulation sim, Faction faction,
                                    MarineArmorCatalogDef armor, String weaponId,
                                    int x, int y, int zone) {
        int squadId = sim.mintSquad(faction, UnitType.MARINE);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < SQUAD_SIZE; i++) {
            EntitySpec spec = new EntitySpec(faction + "-" + squadId + "-" + i,
                    faction, UnitType.MARINE, x + i % 2, y + i / 2).squad(squadId);
            new MarineLoadout(UnitRole.COMBATANT, null, weaponId, EquipmentGrade.SERVICE,
                    SoldierProfile.REGULAR, null, 0, null, armor.appearanceFamily(),
                    armor.armorCapacity(), armor.armorRating(), armor.moveSpeedMult(),
                    armor.incomingAccuracyMult(), null, null, armor.armorClass()).seedInto(spec);
            members.add(sim.spawn(spec));
        }
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = SQUAD_SIZE;
        squad.aliveMembers = SQUAD_SIZE;
        squad.assignedObjective = ObjectiveAssignment.clearZone(squadId, zone);
        return members;
    }

    private static int alive(BattleSimulation sim, List<Long> members) {
        int count = 0;
        for (long unit : members) if (sim.world().isAlive(unit)) count++;
        return count;
    }

    private static float remainingArmor(BattleSimulation sim, List<Long> members) {
        float armor = 0f;
        for (long unit : members) {
            if (sim.world().isAlive(unit) && sim.world().hasArmor(unit)) armor += sim.world().armor(unit);
        }
        return armor;
    }

    private static String caption(String loop, BattleSimulation sim,
                                  List<Long> friendlies, List<Long> defenders, int tick) {
        return String.format(Locale.ROOT, "%s | %.1fs | Marines %d/%d, defenders %d/%d | Same grade and training; no suit systems",
                loop, tick / 30f, alive(sim, friendlies), friendlies.size(),
                alive(sim, defenders), defenders.size());
    }
}
