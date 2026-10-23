package com.dillon.starsectormarines.battle.probe;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.HitLog;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Asks a live battle questions: one simulation held in memory, stepped forward on command, and read between steps.
 *
 * <p>The point is to stop paying a whole replay per question. A battle is deterministic, so stepping to the tick that
 * matters and then asking ten things about it costs one replay; turning on the {@link HitLog} only from that tick
 * costs nothing before it. Every command reads; none changes the battle except {@code advance}/{@code until}, which
 * step it exactly as a harness does, and {@code hitlog}, which only switches an observer.
 *
 * <p>Commands, one per line ({@code #} starts a comment):
 * <pre>
 *   tick                                  current tick, whether the battle is over and who won
 *   advance N                             step N ticks
 *   until T                               step until tick T
 *   until-beat TEXT [max=T]               step until a story beat whose id contains TEXT is published
 *   hitlog on|off|clear|status            record every resolved hit from now (an observer; changes nothing)
 *   entity ID                             everything readable about one body
 *   near ID R | near-cell X Y R           bodies within R cells, nearest first
 *   find [label=TEXT]                      bodies whose label (FACTION:TYPE) contains TEXT
 *   hits [target=ID] [attacker=ID] [label=TEXT] [targetLabel=TEXT] [attackerLabel=TEXT]
 *        [since=T] [until=T] [died=true] [limit=N]
 *                                         recorded hits, oldest first (limit keeps the newest N, default 50)
 *   hits-by attacker|target [same filters]
 *                                         recorded hits grouped by the other side's label: count, damage, kills
 *   squads [label=TEXT]                   every squad with members alive: size, leader position, assignment
 *   beats [since=T] [text=TEXT]           story beats
 *   escort                                the rescue cohort's escort hold and progress
 *   route X0 Y0 X1 Y1                     cells on the walkable path between two cells (0 for none)
 *   places                                every compound: kind, box, holder, capture and who is present
 *   creep                                 hive creep: cells creeped and dying, shares of the map, each source's reach
 * </pre>
 * Every command answers one JSON object.
 */
public final class BattleProbe {

    private final BattleSimulation sim;
    private final Map<String, Command> commands = new LinkedHashMap<>();

    /** A read that may fail: a column the body does not carry, or the JSON library's checked exception. */
    private interface Read {
        Object get() throws Exception;
    }

    private interface Command {
        JSONObject run(Args args) throws Exception;
    }

    public BattleProbe(BattleSimulation sim) {
        this.sim = sim;
        commands.put("tick", a -> status());
        commands.put("advance", a -> step(sim.getSimTickIndex() + a.intAt(0), null));
        commands.put("until", a -> step(a.intAt(0), null));
        commands.put("until-beat", a -> step(a.intOr("max", Integer.MAX_VALUE), a.word(0)));
        commands.put("hitlog", this::hitlog);
        commands.put("entity", a -> entity(a.longAt(0)));
        commands.put("near", this::near);
        commands.put("near-cell", this::nearCell);
        commands.put("find", this::find);
        commands.put("hits", this::hits);
        commands.put("hits-by", this::hitsBy);
        commands.put("squads", this::squads);
        commands.put("beats", this::beats);
        commands.put("route", a -> {
            int[] path = GridPathfinder.findPath(sim.getGrid(), a.intAt(0), a.intAt(1), a.intAt(2), a.intAt(3));
            return new JSONObject().put("tick", sim.getSimTickIndex()).put("cells", Paths.cellCount(path));
        });
        commands.put("escort", a -> new JSONObject().put("tick", sim.getSimTickIndex())
                .put("hold", String.valueOf(sim.getCivilianEscortHold()))
                .put("progress", String.valueOf(sim.getCivilianEvacuationProgress())));
        commands.put("places", this::places);
        commands.put("pads", this::pads);
        commands.put("creep", a -> creep());
    }

    public BattleSimulation sim() {
        return sim;
    }

    /** Runs one command line; a malformed or failing command answers {@code error} rather than ending the session. */
    public JSONObject run(String line) throws Exception {
        Args args = Args.parse(line);
        Command command = commands.get(args.name);
        if (command == null) return new JSONObject().put("error", "unknown command: " + args.name)
                .put("commands", new JSONArray(commands.keySet()));
        try {
            return command.run(args);
        } catch (Exception e) {
            return new JSONObject().put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** The creep field's reading: creeped and dying cells against the map and its walkable ground, and every living source's reach. */
    private JSONObject creep() throws Exception {
        CreepField field = sim.creepFieldIfPresent();
        int cells = sim.getGrid().getWidth() * sim.getGrid().getHeight();
        int walkable = 0, walkableCreeped = 0;
        for (int y = 0; y < sim.getGrid().getHeight(); y++)
            for (int x = 0; x < sim.getGrid().getWidth(); x++) {
                if (!sim.getGrid().isWalkable(x, y)) continue;
                walkable++;
                if (field != null && field.isCreeped(x, y)) walkableCreeped++;
            }
        JSONObject out = new JSONObject().put("tick", sim.getSimTickIndex()).put("mapCells", cells).put("walkableCells", walkable);
        if (field == null) return out.put("covered", 0).put("dying", 0).put("walkableCreeped", 0).put("sources", new JSONArray());
        CreepSnapshot snapshot = field.snapshot();
        JSONArray sources = new JSONArray();
        for (CreepSnapshot.SourceView s : snapshot.sources())
            sources.put(new JSONObject().put("id", s.id()).put("x", s.x()).put("y", s.y())
                    .put("reach", s.reachCells()).put("max", s.maxReachCells()).put("claimed", s.claimedCells()).put("fed", s.fed()));
        return out.put("covered", snapshot.coveredCells()).put("dying", snapshot.dyingCells())
                .put("walkableCreeped", walkableCreeped).put("sources", sources);
    }

    // --- stepping ---

    private JSONObject status() throws Exception {
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("complete", sim.isComplete())
                .put("winner", sim.isComplete() ? String.valueOf(sim.getWinner()) : JSONObject.NULL);
    }

    private JSONObject step(int untilTick, String beat) throws Exception {
        int seen = sim.getStoryBeats().size();
        String matched = null;
        while (!sim.isComplete() && sim.getSimTickIndex() < untilTick) {
            sim.advance(BattleSimulation.TICK_DT);
            if (beat == null) continue;
            List<StoryBeatLog.Event> events = sim.getStoryBeats();
            for (int i = seen; i < events.size() && matched == null; i++)
                if (events.get(i).id().contains(beat)) matched = events.get(i).id();
            seen = events.size();
            if (matched != null) break;
        }
        JSONObject out = status();
        if (beat != null) out.put("beat", matched == null ? JSONObject.NULL : matched);
        return out;
    }

    private JSONObject hitlog(Args a) throws Exception {
        HitLog log = sim.telemetry().hitLog();
        switch (a.word(0)) {
            case "on" -> log.start();
            case "off" -> log.stop();
            case "clear" -> log.clear();
            case "status" -> { }
            default -> throw new IllegalArgumentException("hitlog on|off|clear|status");
        }
        return new JSONObject().put("recording", log.recording()).put("rows", log.hits().size())
                .put("overflowed", log.overflowed()).put("tick", sim.getSimTickIndex());
    }

    // --- bodies ---

    /** Every body the battle currently holds: roster units, ground hulls, craft. */
    private Set<Long> bodies() {
        Set<Long> ids = new LinkedHashSet<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) ids.add(sim.liveUnitAt(i));
        for (long id : sim.getConvoyVehicleIds()) ids.add(id);
        for (long id : sim.getAirEntityIds()) ids.add(id);
        return ids;
    }

    private String label(long id) {
        return HitLog.label(sim.getRoster(), id);
    }

    private JSONObject brief(long id) throws Exception {
        World world = sim.world();
        JSONObject row = new JSONObject().put("id", id).put("label", label(id));
        put(row, "x", () -> round(world.x(id)));
        put(row, "y", () -> round(world.y(id)));
        put(row, "hp", () -> round(world.hp(id)));
        put(row, "armor", () -> round(world.armor(id)));
        put(row, "alive", () -> world.isAlive(id) || sim.convoy().isTargetable(id));
        return row;
    }

    private JSONObject entity(long id) throws Exception {
        World world = sim.world();
        ConvoyService convoy = sim.convoy();
        JSONObject out = brief(id);
        put(out, "maxHp", () -> round(world.maxHp(id)));
        put(out, "maxArmor", () -> round(world.maxArmor(id)));
        put(out, "armorRating", () -> round(world.armorRating(id)));
        if (convoy.isVehicle(id)) {
            JSONObject vehicle = new JSONObject();
            put(vehicle, "type", () -> String.valueOf(convoy.vehicleType(id)));
            put(vehicle, "state", () -> String.valueOf(convoy.mission(id).state));
            GroundBody body = convoy.body(id);
            if (body != null) {
                vehicle.put("facing", round(body.facingDegrees)).put("speed", round(body.speed))
                        .put("immobilised", body.immobilised);
            }
            GroundTurret turret = convoy.turret(id);
            if (turret != null) {
                JSONObject t = new JSONObject().put("facing", round(turret.facingDeg)).put("ammo", turret.ammo)
                        .put("destroyed", turret.destroyed()).put("cooldown", round(turret.cooldownTimer));
                if (turret.targetId != 0) t.put("target", brief(turret.targetId));
                vehicle.put("turret", t);
            }
            put(vehicle, "range", () -> round(convoy.weaponRange(id)));
            out.put("vehicle", vehicle);
        } else if (sim.identity().has(id)) {
            put(out, "name", () -> sim.identity().name(id));
            put(out, "reflex", () -> world.lastReflex(id));
            put(out, "weapon", () -> {
                WeaponDef weapon = sim.combat().primaryWeapon(id);
                return weapon == null ? JSONObject.NULL : weapon.id;
            });
            put(out, "range", () -> round(world.attackRange(id)));
            put(out, "target", () -> {
                long target = world.targetId(id);
                return target == 0 ? JSONObject.NULL : brief(target);
            });
            put(out, "velocity", () -> new JSONArray(List.of(round(sim.movement().velX(id)), round(sim.movement().velY(id)))));
            put(out, "waypointsLeft", () -> sim.movement().waypointCount(id) - sim.movement().nextWaypointIndex(id));
            if (sim.squad().hasSquad(id)) {
                Squad squad = sim.getSquad(sim.squad().squadId(id));
                if (squad != null) out.put("squad", squad(squad));
            }
        }
        if (sim.telemetry().isRecorded(id)) {
            var t = sim.telemetry();
            out.put("telemetry", new JSONObject().put("rounds", t.roundsFired(id)).put("hits", t.roundsHit(id))
                    .put("dealt", round(t.damageDealt(id))).put("taken", round(t.damageTaken(id)))
                    .put("mitigated", round(t.damageMitigated(id))).put("kills", t.kills(id))
                    .put("friendlyFire", round(t.friendlyFireDamage(id))));
        }
        return out;
    }

    private JSONObject near(Args a) throws Exception {
        long id = a.longAt(0);
        return within(sim.world().x(id), sim.world().y(id), a.floatAt(1), id);
    }

    private JSONObject nearCell(Args a) throws Exception {
        return within(a.floatAt(0), a.floatAt(1), a.floatAt(2), 0L);
    }

    private JSONObject within(float x, float y, float radius, long except) throws Exception {
        World world = sim.world();
        List<long[]> found = new ArrayList<>();
        for (long id : bodies()) {
            if (id == except) continue;
            float dx, dy;
            try {
                dx = world.x(id) - x;
                dy = world.y(id) - y;
            } catch (RuntimeException e) {
                continue;
            }
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d <= radius) found.add(new long[]{id, Float.floatToIntBits(d)});
        }
        found.sort(Comparator.comparingDouble(f -> Float.intBitsToFloat((int) f[1])));
        List<JSONObject> rows = new ArrayList<>();
        for (long[] f : found) rows.add(brief(f[0]).put("dist", round(Float.intBitsToFloat((int) f[1]))));
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("x", round(x)).put("y", round(y))
                .put("count", rows.size()).put("bodies", new JSONArray(rows));
    }

    private JSONObject find(Args a) throws Exception {
        String text = a.option("label", "");
        List<JSONObject> rows = new ArrayList<>();
        for (long id : bodies()) if (label(id).contains(text)) rows.add(brief(id));
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("count", rows.size())
                .put("bodies", new JSONArray(rows));
    }

    private JSONObject squad(Squad squad) throws Exception {
        JSONObject row = new JSONObject().put("id", squad.id).put("faction", String.valueOf(squad.faction))
                .put("alive", squad.aliveMembers).put("label", squad.campaignLabel == null ? JSONObject.NULL : squad.campaignLabel)
                .put("authority", String.valueOf(squad.assignedAuthority))
                .put("assignment", squad.assignedObjective == null ? JSONObject.NULL : assignment(squad.assignedObjective))
                .put("alert", String.valueOf(squad.alertLevel));
        long leader = squad.leaderId;
        put(row, "leader", () -> leader == 0 ? JSONObject.NULL : brief(leader));
        row.put("goal", squad.currentGoal == null ? JSONObject.NULL : squad.currentGoal.name())
                .put("post", squad.assignedNode != null);
        int members = sim.squadMemberCount(squad.id);
        if (members > 0) {
            float x = 0f;
            float y = 0f;
            for (int i = 0; i < members; i++) {
                x += sim.world().x(sim.squadMemberAt(squad.id, i));
                y += sim.world().y(sim.squadMemberAt(squad.id, i));
            }
            row.put("centroid", new JSONArray(List.of(round(x / members), round(y / members))));
        }
        return row;
    }

    private static JSONObject assignment(ObjectiveAssignment a) throws Exception {
        JSONObject out = new JSONObject().put("kind", String.valueOf(a.kind()));
        if (a.targetCellX() >= 0) out.put("cell", new JSONArray(List.of(a.targetCellX(), a.targetCellY())));
        if (a.targetRadiusCells() > 0) out.put("radius", a.targetRadiusCells());
        if (a.targetNode() != null) out.put("node", node(a.targetNode()));
        if (a.targetZoneId() >= 0) out.put("zone", a.targetZoneId());
        if (a.objectiveId() >= 0) out.put("objective", a.objectiveId());
        return out;
    }

    private static JSONObject node(TacticalNode n) throws Exception {
        return new JSONObject().put("kind", String.valueOf(n.kind)).put("anchor", new JSONArray(List.of(n.anchorX, n.anchorY)))
                .put("box", new JSONArray(List.of(n.left, n.top, n.right, n.bottom)));
    }

    private JSONObject places(Args a) throws Exception {
        JSONArray rows = new JSONArray();
        for (CompoundService.Record r : sim.getCompoundService().getRecords()) {
            rows.put(node(r.node).put("state", String.valueOf(r.state)).put("capture", round(r.captureProgress))
                    .put("hold", round(r.holdTimer)).put("attackersPresent", r.attackersPresent)
                    .put("defendersPresent", r.defendersPresent).put("mustHold", r.node.mustHold)
                    .put("priority", r.node.priorityScore));
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("places", rows);
    }

    /** The marine landing berths setup recorded: where each shuttle touches down. */
    private JSONObject pads(Args a) throws Exception {
        JSONArray rows = new JSONArray();
        for (LandingPad pad : sim.getMarineLandingPads()) {
            rows.put(new JSONObject().put("x", pad.centerX).put("y", pad.centerY)
                    .put("purpose", String.valueOf(pad.purpose)).put("approach", String.valueOf(pad.approach)));
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("pads", rows);
    }

    private JSONObject squads(Args a) throws Exception {
        String text = a.option("label", "");
        JSONArray rows = new JSONArray();
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0) continue;
            JSONObject row = squad(squad);
            if (row.toString().contains(text)) rows.put(row);
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("count", rows.length()).put("squads", rows);
    }

    private JSONObject beats(Args a) throws Exception {
        int since = a.intOr("since", Integer.MIN_VALUE);
        String text = a.option("text", "");
        JSONArray rows = new JSONArray();
        for (var e : sim.getStoryBeats())
            if (e.tick() >= since && e.id().contains(text)) rows.put(new JSONObject().put("tick", e.tick()).put("id", e.id()));
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("beats", rows);
    }

    // --- hits ---

    private List<HitLog.Hit> filtered(Args a) {
        long target = a.longOr("target", -1L);
        long attacker = a.longOr("attacker", -1L);
        String text = a.option("label", "");
        String targetText = a.option("targetLabel", "");
        String attackerText = a.option("attackerLabel", "");
        int since = a.intOr("since", Integer.MIN_VALUE);
        int until = a.intOr("until", Integer.MAX_VALUE);
        boolean diedOnly = Boolean.parseBoolean(a.option("died", "false"));
        List<HitLog.Hit> out = new ArrayList<>();
        for (HitLog.Hit hit : sim.telemetry().hitLog().hits()) {
            if (target >= 0 && hit.target() != target) continue;
            if (attacker >= 0 && hit.attacker() != attacker) continue;
            if (hit.tick() < since || hit.tick() > until) continue;
            if (diedOnly && !hit.died()) continue;
            if (!text.isEmpty() && !hit.targetLabel().contains(text) && !hit.attackerLabel().contains(text)) continue;
            if (!hit.targetLabel().contains(targetText) || !hit.attackerLabel().contains(attackerText)) continue;
            out.add(hit);
        }
        return out;
    }

    private JSONObject hits(Args a) throws Exception {
        List<HitLog.Hit> hits = filtered(a);
        int limit = a.intOr("limit", 50);
        JSONArray rows = new JSONArray();
        for (HitLog.Hit hit : hits.subList(Math.max(0, hits.size() - limit), hits.size())) rows.put(hit(hit));
        return new JSONObject().put("matched", hits.size()).put("shown", rows.length()).put("hits", rows);
    }

    private static JSONObject hit(HitLog.Hit hit) throws Exception {
        JSONObject row = new JSONObject().put("tick", hit.tick())
                .put("attacker", hit.attacker()).put("attackerLabel", hit.attackerLabel())
                .put("target", hit.target()).put("targetLabel", hit.targetLabel())
                .put("damage", round(hit.damage())).put("postCover", round(hit.postCover()))
                .put("pen", round(hit.penetration())).put("rating", round(hit.rating()))
                .put("armorDmg", round(hit.armorDamage())).put("structDmg", round(hit.structureDamage()))
                .put("hpAfter", round(hit.hpAfter())).put("armorAfter", round(hit.armorAfter()));
        if (hit.arc() != null) row.put("arc", hit.arc());
        if (hit.part() != null) row.put("part", hit.part());
        if (hit.mitigated() > 0f) row.put("mitigated", round(hit.mitigated()));
        if (hit.died()) row.put("died", true);
        return row;
    }

    private JSONObject hitsBy(Args a) throws Exception {
        boolean byAttacker = switch (a.word(0)) {
            case "attacker" -> true;
            case "target" -> false;
            default -> throw new IllegalArgumentException("hits-by attacker|target");
        };
        Map<String, float[]> groups = new HashMap<>();
        // count, applied, armor, structure, kills, pen sum, rating sum, first tick, last tick
        for (HitLog.Hit hit : filtered(a)) {
            String key = byAttacker ? hit.attackerLabel() : hit.targetLabel();
            float[] g = groups.computeIfAbsent(key, k -> new float[]{0, 0, 0, 0, 0, 0, 0, Float.MAX_VALUE, 0});
            g[0]++;
            g[1] += hit.applied();
            g[2] += hit.armorDamage();
            g[3] += hit.structureDamage();
            if (hit.died()) g[4]++;
            g[5] += hit.penetration();
            g[6] += hit.rating();
            g[7] = Math.min(g[7], hit.tick());
            g[8] = Math.max(g[8], hit.tick());
        }
        List<Map.Entry<String, float[]>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort((x, y) -> Float.compare(y.getValue()[1], x.getValue()[1]));
        JSONArray rows = new JSONArray();
        for (var e : sorted) {
            float[] g = e.getValue();
            rows.put(new JSONObject().put(byAttacker ? "attacker" : "target", e.getKey()).put("hits", (int) g[0])
                    .put("applied", round(g[1])).put("armorDmg", round(g[2])).put("structDmg", round(g[3]))
                    .put("kills", (int) g[4]).put("meanPen", round(g[5] / g[0])).put("meanRating", round(g[6] / g[0]))
                    .put("firstTick", (int) g[7]).put("lastTick", (int) g[8]));
        }
        return new JSONObject().put("by", byAttacker ? "attacker" : "target").put("groups", rows);
    }

    // --- helpers ---

    /** A read that a body without the column would fail loudly on is reported as absent rather than thrown. */
    private static void put(JSONObject row, String key, Read value) {
        try {
            Object v = value.get();
            row.put(key, v == null ? JSONObject.NULL : v);
        } catch (Exception e) {
            // Not every body carries every column.
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** {@code name word word key=value ...} */
    static final class Args {
        final String name;
        final List<String> words = new ArrayList<>();
        final Map<String, String> options = new HashMap<>();

        private Args(String name) {
            this.name = name;
        }

        static Args parse(String line) {
            String[] parts = line.trim().split("\\s+");
            Args args = new Args(parts[0].toLowerCase(Locale.ROOT));
            for (int i = 1; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq > 0) args.options.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
                else args.words.add(parts[i]);
            }
            return args;
        }

        String word(int i) {
            if (i >= words.size()) throw new IllegalArgumentException(name + ": missing argument " + (i + 1));
            return words.get(i);
        }

        int intAt(int i) { return Integer.parseInt(word(i)); }
        long longAt(int i) { return Long.parseLong(word(i)); }
        float floatAt(int i) { return Float.parseFloat(word(i)); }
        String option(String key, String fallback) { return options.getOrDefault(key, fallback); }
        int intOr(String key, int fallback) { return options.containsKey(key) ? Integer.parseInt(options.get(key)) : fallback; }
        long longOr(String key, long fallback) { return options.containsKey(key) ? Long.parseLong(options.get(key)) : fallback; }
    }
}
