package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
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

/** Ordinary catalog actors walking into a finite firefight; no balance claim. */
public final class SpecterBattleScene implements BehaviorScene {
    public static final int WIDTH = 112;
    public static final int HEIGHT = 64;
    public static final int ORIGIN_X = (WIDTH - 80) / 2;
    public static final int ORIGIN_Y = (HEIGHT - 45) / 2;
    public static final int TICKS = 450;
    public static final int FRAME_EVERY_TICKS = 3;
    public static final String LOOP = "march-and-contact";
    public static final String SPECTER_ARMOR = "armor.specter-heavy";
    public static final String REGULAR_ARMOR = "armor.line";

    @Override public String id() { return "specter-battle"; }
    @Override public String label() { return "Specter, Legionary and Hound: actual march and combat"; }
    @Override public int frameDelayMillis() { return 100; }

    @Override public List<SceneReport> play(FrameSink frames) throws Exception {
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
            return List.of(replay(frames));
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private SceneReport replay(FrameSink frames) {
        SceneWorld scene = SceneBuilder.openGround(WIDTH, HEIGHT)
                .wall(ORIGIN_X + 42, ORIGIN_Y + 3, ORIGIN_X + 60, ORIGIN_Y + 4)
                .wall(ORIGIN_X + 42, ORIGIN_Y + 40, ORIGIN_X + 60, ORIGIN_Y + 41)
                .squad("hound").size(1).at(ORIGIN_X + 18, ORIGIN_Y + 35)
                .mech(MechVariant.HOUND, MechRole.BALANCED)
                .assigned(id -> ObjectiveAssignment.attackMove(id, ORIGIN_X + 64, ORIGIN_Y + 35))
                .done().build();
        BattleSimulation sim = scene.sim();
        try {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    sim.getTopology().setGroundKind(x, y,
                            y >= ORIGIN_Y + 20 && y <= ORIGIN_Y + 29
                                    ? GroundKind.STREET : GroundKind.DIRT);
                }
            }
            MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
            MarineArmorCatalogDef specter = catalog.get(SPECTER_ARMOR);
            MarineArmorCatalogDef regular = catalog.get(REGULAR_ARMOR);
            Map<String, List<Long>> groups = new LinkedHashMap<>();
            groups.put("specter", spawn(sim, "Specter", Faction.MARINE, specter, 18, 13, false));
            groups.put("regular", spawn(sim, "Legionary", Faction.MARINE, regular, 18, 25, false));
            groups.put("hound", List.of(scene.members("hound")[0]));
            for (int y : new int[]{13, 25, 35}) {
                spawn(sim, "Picket", Faction.DEFENDER, regular, 54, y, true);
            }
            Map<Long, float[]> starts = new LinkedHashMap<>();
            Map<Long, Double> distance = new LinkedHashMap<>();
            Map<Long, Integer> shots = new LinkedHashMap<>();
            Map<String, Integer> identityCounts = new LinkedHashMap<>();
            for (Map.Entry<String, List<Long>> group : groups.entrySet()) {
                int matches = 0;
                for (long id : group.getValue()) {
                    boolean match = group.getKey().equals("hound")
                            ? sim.identity().mechVariant(id) == MechVariant.HOUND
                            : sim.identity().armorClass(id) == (group.getKey().equals("specter")
                                    ? specter.armorClass() : regular.armorClass());
                    if (match) matches++;
                }
                identityCounts.put(group.getKey(), matches);
            }
            for (List<Long> group : groups.values()) for (long id : group) {
                starts.put(id, new float[]{sim.world().x(id), sim.world().y(id)});
                distance.put(id, 0d);
                shots.put(id, 0);
            }
            int defenderShots = 0;
            int firstShotTick = -1;
            Map<String, Integer> movedBeforeContact = new LinkedHashMap<>();
            for (int tick = 0; tick < TICKS; tick++) {
                if (tick % FRAME_EVERY_TICKS == 0) frames.frame(LOOP, sim, tick, caption(tick));
                sim.advance(BattleSimulation.TICK_DT);
                frames.advanced(LOOP, sim, tick);
                for (Map.Entry<Long, float[]> entry : starts.entrySet()) {
                    long id = entry.getKey();
                    if (!sim.world().isAlive(id)) continue;
                    float[] last = entry.getValue();
                    float x = sim.world().x(id), y = sim.world().y(id);
                    distance.put(id, distance.get(id) + Math.hypot(x - last[0], y - last[1]));
                    last[0] = x;
                    last[1] = y;
                }
                for (ShotEvent shot : sim.getShotsThisFrame()) {
                    if (shots.containsKey(shot.shooterId)) shots.put(shot.shooterId, shots.get(shot.shooterId) + 1);
                    if (shot.shooterFaction == Faction.DEFENDER) defenderShots++;
                    if (firstShotTick < 0) {
                        firstShotTick = tick + 1;
                        for (Map.Entry<String, List<Long>> group : groups.entrySet()) {
                            int moved = 0;
                            for (long id : group.getValue()) if (distance.get(id) > 0.5d) moved++;
                            movedBeforeContact.put(group.getKey(), moved);
                        }
                    }
                }
            }
            frames.frame(LOOP, sim, TICKS, caption(TICKS));
            Map<String, Number> metrics = new LinkedHashMap<>();
            List<Verdict> verdicts = new ArrayList<>();
            for (Map.Entry<String, List<Long>> group : groups.entrySet()) {
                String key = group.getKey();
                int moved = 0, fired = 0, alive = 0;
                double travel = 0d;
                for (long id : group.getValue()) {
                    if (distance.get(id) > 0.5d) moved++;
                    fired += shots.get(id);
                    travel += distance.get(id);
                    if (sim.world().isAlive(id)) alive++;
                }
                metrics.put(key + "Starting", group.getValue().size());
                metrics.put(key + "MovedMembers", moved);
                metrics.put(key + "TravelCells", travel);
                metrics.put(key + "Shots", fired);
                metrics.put(key + "Survivors", alive);
                metrics.put(key + "MatchingIdentities", identityCounts.get(key));
                int preContactMoved = movedBeforeContact.getOrDefault(key, 0);
                metrics.put(key + "MovedBeforeContact", preContactMoved);
                verdicts.add(Verdict.of(key + "-walked-before-contact", preContactMoved > 0,
                        preContactMoved + " actors physically travelled before the first shot"));
                verdicts.add(Verdict.of(key + "-identity", identityCounts.get(key) == group.getValue().size(),
                        "Spawned catalog identity retained for every actor"));
                verdicts.add(Verdict.of(key + "-walked", moved == group.getValue().size(), moved + " actors physically travelled over half a cell"));
                verdicts.add(Verdict.of(key + "-fired", fired > 0, fired + " actual production shot events"));
            }
            metrics.put("defenderShots", defenderShots);
            metrics.put("firstShotTick", firstShotTick);
            metrics.put("seed", BattleSimulation.DEFAULT_SEED);
            metrics.put("specterArmorCapacity", specter.armorCapacity());
            metrics.put("regularArmorCapacity", regular.armorCapacity());
            verdicts.add(Verdict.of("opposition-fired", defenderShots > 0, defenderShots + " hostile shots; outcomes are observations, not balance acceptance"));
            return new SceneReport(id(), LOOP, TICKS, verdicts, metrics);
        } finally {
            sim.close();
        }
    }

    private static List<Long> spawn(BattleSimulation sim, String name, Faction faction,
                                   MarineArmorCatalogDef armor, int x, int y, boolean defend) {
        int squadId = sim.mintSquad(faction, UnitType.MARINE);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            EntitySpec spec = new EntitySpec(name + "-" + squadId + "-" + i,
                    faction, UnitType.MARINE, ORIGIN_X + x + i % 4, ORIGIN_Y + y + i / 4).squad(squadId);
            new MarineLoadout(UnitRole.COMBATANT, null, WeaponRegistry.STARTER_PRIMARY_ID,
                    EquipmentGrade.SERVICE, SoldierProfile.REGULAR, null, 0, null,
                    armor.appearanceFamily(), armor.armorCapacity(), armor.armorRating(),
                    armor.moveSpeedMult(), armor.incomingAccuracyMult(), null,
                    armor.integralSystem(), armor.armorClass()).seedInto(spec);
            members.add(sim.spawn(spec));
        }
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = members.size();
        squad.aliveMembers = members.size();
        squad.assignedObjective = ObjectiveAssignment.attackMove(squadId,
                ORIGIN_X + (defend ? x : 64), ORIGIN_Y + y);
        if (defend) sim.getSquadMoveOrderService().requestDefendArea(squadId, ORIGIN_X + x, ORIGIN_Y + y);
        else sim.getSquadMoveOrderService().requestMove(squadId, ORIGIN_X + 64, ORIGIN_Y + y);
        return members;
    }

    private static String caption(int tick) {
        return String.format(Locale.ROOT,
                "%.1fs | Specter battlesuits + Legionary marines + Hound | Real orders and combat | 1x simulation",
                tick / 30f);
    }
}
