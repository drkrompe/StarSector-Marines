package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.marine.MechFitSpace;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.battleview.BattleSoundLog;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Every weapon we have, fired once on camera with its sound, for the owner to judge how each one sounds and feels: a
 * clip per weapon through the look preset on real ground, the shooter in the west of the frame and a target in the
 * east (a parked heavy APC with its gun silenced, kept whole, and three unarmed militia dummies in front of it).
 *
 * <ul>
 *   <li><b>Mechs</b>: every {@link MechWeaponComponent}, alone on one mount of a chassis whose socket takes it (the
 *       chassis that stocks it first), every other mount empty.</li>
 *   <li><b>Vehicles</b>: every hull with a gun of its own, in its standard part, plus the Onager's other turret heads
 *       (autocannon, rotary) and the laser tank's red head; the light-machine-gun hulls share one gun, so only the
 *       heavy APC fires it.</li>
 *   <li><b>Infantry</b>: every marine primary, and every special that fires a weapon, its carrier's own primary taken
 *       away so only the special is heard; a thrown or sprayed special (grenades, the flamer), which the AI reaches
 *       for only alongside its rifle, is thrown on a script instead.</li>
 * </ul>
 *
 * Each clip is found first: the scene is played unrecorded until its first round ({@link #LEAD_TICKS} before it the
 * recording starts, so no clip waits on its own aim), then played again from the start and recorded from there for
 * the weapon's {@link Entry#clipTicks}. The scene is deterministic, so both plays fire at the same tick; the manifest
 * notes the tick the recording actually saw. Fire is autonomous, as the battle's own AI holds the trigger: a held gun
 * fires its bursts and lets go as it would in a fight, not on a script.
 *
 * <p>Writes {@code look/<category>-<key>-reel.mp4} per weapon, 1920x1080 at 30 fps, and
 * {@code build/snapshots/weapon-reel/manifest.json} (each clip's weapon, name, role, firing mode, sound ids, shots
 * and first-shot frame, and the weapons left out with why). Run with {@code -Dsnapshot.lookSound=true} for the
 * soundtrack; the suite keeps it to the guns ({@link BattleSoundLog#WEAPONS_ONLY_PROPERTY}) unless that is set false.
 * {@code tools/weapon_reel.py} runs it and cuts the clips into labelled reels by category.
 * {@code -Dsnapshot.weaponReel.only=<key or category>[,...]} narrows it. Opt-in.
 */
public final class WeaponReelSuite implements SnapshotSuite {

    static final int W = 64, H = 36;
    /** The recording starts this many ticks (half a second) before the first round. */
    static final int LEAD_TICKS = 15;
    /** How long the unrecorded play waits for a first round before the clip is left out. */
    static final int MAX_WAIT_TICKS = 1200;
    /** Dummies that no clip can kill. */
    static final float DUMMY_HEALTH = 50_000f;
    static final LookGround GROUND = LookGround.verdant();
    /** A scripted throw's cadence: the first half a second in, then every second and a half. */
    static final int THROW_FIRST_TICK = 15, THROW_EVERY_TICKS = 45;
    /** The target hull: a heavy APC, its gun silenced, kept whole. */
    static final VehicleType TARGET_HULL = VehicleType.HEAVY_APC;
    static final String FIELD_MORTAR_WEAPON_ID = "weapon.field-mortar";

    public enum Category { MECH, VEHICLE, INFANTRY }

    /** What a clip stood up: the simulation, and a step to run before every tick (pinning hulls, mending the target). */
    record Staged(BattleSimulation sim, Consumer<BattleSimulation> step, Set<Long> target) {

        /** The subject's rounds this frame: anything not fired by the target. */
        int rounds() {
            int n = 0;
            for (ShotEvent shot : sim.getShotsThisFrame()) if (!target.contains(shot.shooterId)) n++;
            return n;
        }
    }

    interface Stager {
        Staged stage(LookReview.Placement placement, float shooterX, float shooterY, float targetX, float targetY);
    }

    /**
     * One clip.
     *
     * @param distance  cells from shooter to target
     * @param clipTicks ticks recorded from the lead-in
     */
    record Entry(Category category, String key, String weaponId, String name, String role, String mode,
                 String shooter, String fireSound, String impactSound, String loopSound,
                 float distance, int clipTicks, Stager stager) {

        String shot() {
            return category.name().toLowerCase(Locale.ROOT) + "-" + key;
        }
    }

    @Override public String id() { return "weapon-reel"; }

    @Override public String label() {
        return "Weapon reel: every mech, vehicle and infantry weapon fired at a target, one clip each, to hear with"
                + " -Dsnapshot.lookSound=true";
    }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        String previous = System.getProperty(BattleSoundLog.WEAPONS_ONLY_PROPERTY);
        if (previous == null) System.setProperty(BattleSoundLog.WEAPONS_ONLY_PROPERTY, "true");
        try (HeadlessStrings strings = new HeadlessStrings(context.modRoot())) {
            return renderAll(context);
        } finally {
            if (previous == null) System.clearProperty(BattleSoundLog.WEAPONS_ONLY_PROPERTY);
        }
    }

    private List<SnapshotArtifact> renderAll(SnapshotContext context) throws Exception {
        Set<String> only = new LinkedHashSet<>();
        for (String s : System.getProperty("snapshot.weaponReel.only", "").split(",")) {
            if (!s.isBlank()) only.add(s.trim().toLowerCase(Locale.ROOT));
        }
        List<String> skipped = new ArrayList<>();
        List<Entry> entries = entries(skipped);
        List<SnapshotArtifact> out = new ArrayList<>();
        JSONArray clips = new JSONArray();
        JSONArray left = new JSONArray();
        for (String s : skipped) left.put(s);
        for (Entry entry : entries) {
            String category = entry.category().name().toLowerCase(Locale.ROOT);
            if (!only.isEmpty() && !only.contains(entry.key()) && !only.contains(category)
                    && !only.contains(entry.shot())) continue;
            int first = firstShot(entry);
            if (first < 0) {
                String why = entry.shot() + " (" + entry.weaponId() + "): no round in " + MAX_WAIT_TICKS / 30
                        + " s of play";
                System.out.println("    [weapon-reel] " + why);
                left.put(why);
                continue;
            }
            int preRoll = Math.max(0, first - LEAD_TICKS);
            int[] seen = {-1, 0};
            LookReview.Shot shot = new LookReview.Shot(entry.shot(), GROUND, W, H,
                    placement -> stage(entry, placement, preRoll, seen),
                    placement -> List.of(view(entry, placement)), entry.clipTicks(), 1, 0, null);
            List<SnapshotArtifact> recorded = LookReview.record(context.modRoot(), shot);
            out.addAll(recorded);
            JSONObject clip = new JSONObject();
            clip.put("category", category);
            clip.put("key", entry.key());
            clip.put("shot", entry.shot());
            clip.put("video", "look/" + entry.shot() + "-reel.mp4");
            clip.put("weaponId", entry.weaponId());
            clip.put("name", entry.name());
            clip.put("role", entry.role());
            clip.put("mode", entry.mode());
            clip.put("shooter", entry.shooter());
            clip.put("fireSound", entry.fireSound() == null ? JSONObject.NULL : entry.fireSound());
            clip.put("impactSound", entry.impactSound() == null ? JSONObject.NULL : entry.impactSound());
            clip.put("loopSound", entry.loopSound() == null ? JSONObject.NULL : entry.loopSound());
            clip.put("distanceCells", entry.distance());
            clip.put("fps", 30);
            clip.put("frames", entry.clipTicks() + 1);
            clip.put("firstShotFrame", seen[0]);
            clip.put("rounds", seen[1]);
            clips.put(clip);
            System.out.printf(Locale.ROOT, "    [weapon-reel] %s: first round at tick %d, %d rounds in the clip%n",
                    entry.shot(), first, seen[1]);
        }
        JSONObject manifest = new JSONObject();
        manifest.put("clips", clips);
        manifest.put("leftOut", left);
        Path file = context.projectRoot().resolve("build/snapshots").resolve(id()).resolve("manifest.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, manifest.toString(2));
        return out;
    }

    /**
     * The tick of the scene's first round, played unrecorded from the start; -1 when none comes in
     * {@link #MAX_WAIT_TICKS}.
     */
    private static int firstShot(Entry entry) {
        MapResult map = GROUND.generate();
        LookReview.Placement placement = new LookReview.Placement(GROUND, map, LookGround.openSite(map, W, H));
        Staged staged = place(entry, placement);
        BattleSimulation sim = staged.sim();
        try {
            for (int tick = 0; tick < MAX_WAIT_TICKS; tick++) {
                // A scripted throw lands in the frame's list before the advance; the AI's rounds come with it.
                staged.step().accept(sim);
                if (staged.rounds() > 0) return tick;
                sim.advance(BattleSimulation.TICK_DT);
                BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
            }
            return -1;
        } finally {
            sim.close();
        }
    }

    /**
     * Stands the scene up, plays it unrecorded to {@code preRoll}, then counts the subject's rounds as it records
     * ({@code seen}: the first frame with one, and how many).
     */
    private static BattleSimulation stage(Entry entry, LookReview.Placement placement, int preRoll, int[] seen) {
        Staged staged = place(entry, placement);
        for (int i = 0; i < preRoll; i++) advance(staged);
        int[] frame = {0};
        placement.beforeTick(sim -> {
            // Before tick n the simulation holds the rounds of advance n - 1 (recorded frame n - 1) and, after the
            // step, any round the step itself fired.
            staged.step().accept(sim);
            int rounds = staged.rounds();
            if (rounds > 0) {
                if (seen[0] < 0) seen[0] = Math.max(0, frame[0] - 1);
                seen[1] += rounds;
            }
            frame[0]++;
        });
        return staged.sim();
    }

    /** One tick as {@link LookReview#record} plays it, without the drawing. */
    private static void advance(Staged staged) {
        BattleSimulation sim = staged.sim();
        staged.step().accept(sim);
        sim.advance(BattleSimulation.TICK_DT);
        BuildingVisibilityPass.advanceAlpha(sim.getBuildings(), BattleSimulation.TICK_DT);
    }

    private static Staged place(Entry entry, LookReview.Placement placement) {
        float half = entry.distance() / 2f;
        float[] shooter = placement.openNear(W / 2f - half, H / 2f);
        float[] target = placement.openNear(shooter[0] - placement.site().x0() + entry.distance(), H / 2f);
        return entry.stager().stage(placement, shooter[0], shooter[1], target[0], target[1]);
    }

    /** A fixed camera on the middle of shooter and target, wide enough for both and the ground round them. */
    private static LookReview.View view(Entry entry, LookReview.Placement placement) {
        float half = entry.distance() / 2f;
        float[] shooter = placement.openNear(W / 2f - half, H / 2f);
        float cx = shooter[0] + half;
        float across = Math.max(24f, entry.distance() * 1.45f + 10f);
        return LookReview.View.close("reel", cx, shooter[1], across);
    }

    // ---- the catalogue ----

    private static List<Entry> entries(List<String> skipped) {
        List<Entry> out = new ArrayList<>();
        mechs(out, skipped);
        vehicles(out, skipped);
        infantry(out, skipped);
        return out;
    }

    private static final MechVariant[] CHASSIS = {MechVariant.HOUND, MechVariant.WARDEN, MechVariant.BULWARK};

    private static void mechs(List<Entry> out, List<String> skipped) {
        for (MechWeaponComponent weapon : MechWeaponComponent.values()) {
            MechVariant variant = null;
            MechMountSlot slot = null;
            // The chassis that stocks it, else the first whose socket takes it.
            for (int pass = 0; pass < 2 && variant == null; pass++) {
                for (MechVariant v : CHASSIS) {
                    for (MechMountSlot s : MechMountSlot.values()) {
                        if (!v.hasMount(s) || !MechFitSpace.weaponsFor(v, s).contains(weapon)) continue;
                        if (pass == 0 && v.stockWeapon(s) != weapon) continue;
                        variant = v;
                        slot = s;
                        break;
                    }
                    if (variant != null) break;
                }
            }
            if (variant == null) {
                skipped.add("mech " + weapon.name() + " (" + weapon.weaponId + "): no chassis socket takes it");
                continue;
            }
            WeaponDef def = weapon.weaponDef();
            MechVariant chassis = variant;
            MechMountSlot mount = slot;
            String shooter = chassis.id + " " + mount.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            out.add(new Entry(Category.MECH, kebab(weapon.name()), weapon.weaponId, weapon.displayName(),
                    role(def), mode(def), shooter, def.fireSoundId, def.impactSoundId, def.loopSoundId,
                    distance(def), clipTicks(def),
                    (placement, sx, sy, tx, ty) -> {
                        MechDeploymentSpec fit = new MechDeploymentSpec(chassis, chassis.defaultRole,
                                MissileReplenisherComponent.STANDARD,
                                mount == MechMountSlot.ARMS ? weapon : null,
                                mount == MechMountSlot.LEFT_ARM ? weapon : null,
                                mount == MechMountSlot.RIGHT_ARM ? weapon : null,
                                mount == MechMountSlot.LEFT_SHOULDER ? weapon : null,
                                mount == MechMountSlot.RIGHT_SHOULDER ? weapon : null,
                                null, MechFrame.stock(chassis), null);
                        SceneBuilder builder = placement.builder();
                        int lx = (int) sx - placement.site().x0();
                        int ly = (int) sy - placement.site().y0();
                        builder.squad("shooter").faction(Faction.MARINE).size(1).at(lx, ly).mech(fit).stationary()
                                .assigned(id -> ObjectiveAssignment.defendArea(id, (int) sx, (int) sy)).done();
                        return withTarget(placement, builder, tx, ty, !weapon.antiArmorOnly(), null);
                    }));
        }
    }

    private static void vehicles(List<Entry> out, List<String> skipped) {
        List<VehicleType> hulls = new ArrayList<>();
        for (VehicleType type : VehicleType.values()) {
            if (!type.hasTurretWeapon()) continue;
            // Every machine-gun hull carries the one light machine gun: the heavy APC fires it for them all.
            if (type.ownGun() == MechWeaponComponent.LIGHT_MACHINE_GUN && type != VehicleType.HEAVY_APC) {
                skipped.add("vehicle " + kebab(type.name()) + ": its light machine gun is the heavy APC's clip");
                continue;
            }
            hulls.add(type);
        }
        for (VehicleType type : hulls) vehicle(out, type, type.standardPart(), kebab(type.name()));
        // The Onager's other heads, and the laser tank's red one.
        for (MechWeaponComponent gun : MechWeaponComponent.values()) {
            if (gun == VehicleType.BREAKTHROUGH_TANK.standardPart()) continue;
            MechWeaponComponent.TankHead head = gun.tankHead();
            if (VehicleType.BREAKTHROUGH_TANK.headFrames(head) != null
                    && (gun == MechWeaponComponent.AC_6 || gun == MechWeaponComponent.CHAINGUN)) {
                vehicle(out, VehicleType.BREAKTHROUGH_TANK, gun, "breakthrough-tank-" + kebab(gun.name()));
            }
        }
        vehicle(out, VehicleType.LASER_TANK, MechWeaponComponent.HEAVY_PD_LASER, "laser-tank-heavy-pd-laser");
    }

    private static void vehicle(List<Entry> out, VehicleType type, MechWeaponComponent gun, String key) {
        WeaponDef def = gun != null ? gun.weaponDef() : type.turretStructure().mount.weapon;
        String name = (gun != null ? gun.displayName() : def.displayName()) + " on the " + kebab(type.name());
        out.add(new Entry(Category.VEHICLE, key, def.id, name, role(def), mode(def),
                kebab(type.name()) + (gun != null ? " with " + kebab(gun.name()) : ""), def.fireSoundId,
                def.impactSoundId, def.loopSoundId, distance(def), clipTicks(def),
                (placement, sx, sy, tx, ty) -> {
                    SceneBuilder builder = placement.builder();
                    // A side with no body is not ticked, and a hull is not a body: an unarmed marine far off.
                    float[] post = placement.openNear(-24f, H / 2f);
                    builder.unit("holdout", Faction.MARINE, UnitType.MARINE, (int) post[0] - placement.site().x0(),
                            (int) post[1] - placement.site().y0(), spec -> spec.moveSpeed(0f).attackRange(0f).attackDamage(0f));
                    return withTarget(placement, builder, tx, ty, true, world -> {
                        BattleSimulation sim = world.sim();
                        long hull = sim.convoy().spawn(new VehicleDeploymentSpec(null, null, type, List.of(),
                                        VehiclePlating.FACTORY, VehicleEngine.STOCK, gun),
                                Faction.MARINE, VehicleMission.deployed(sx, sy));
                        float facing = AirBody.facingToward(1f, 0f);
                        sim.convoy().body(hull).teleport(sx, sy, facing);
                        return s -> s.convoy().body(hull).teleport(sx, sy, facing);
                    });
                }));
    }

    /** Marine primaries a rifleman carries, then the specials that fire a weapon. */
    private static void infantry(List<Entry> out, List<String> skipped) {
        WeaponRegistry registry = WeaponRegistry.installed();
        for (WeaponDef def : registry.all()) {
            if (def.mount != MountClass.MARINE_PRIMARY) continue;
            if (def.id.startsWith("weapon.drone-")) {
                skipped.add("infantry " + def.id + ": a drone's gun, fielded by drones rather than marines");
                continue;
            }
            out.add(new Entry(Category.INFANTRY, def.id.substring("weapon.".length()), def.id, def.displayName(),
                    role(def), mode(def), "marine", def.fireSoundId, def.impactSoundId, def.loopSoundId,
                    distance(def), clipTicks(def),
                    (placement, sx, sy, tx, ty) -> {
                        SceneBuilder builder = placement.builder();
                        builder.squad("shooter").faction(Faction.MARINE).size(1)
                                .at((int) sx - placement.site().x0(), (int) sy - placement.site().y0())
                                .primary(def.id).health(DUMMY_HEALTH).stationary().done();
                        return withTarget(placement, builder, tx, ty, true, null);
                    }));
        }
        for (String id : List.of(SpecialEquipmentRegistry.SRM_1_ID, SpecialEquipmentRegistry.SRM_1_QS_ID,
                SpecialEquipmentRegistry.SRM_3_POD_ID, SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                SpecialEquipmentRegistry.FLAMER_ID, SpecialEquipmentRegistry.FRAG_GRENADE_ID,
                SpecialEquipmentRegistry.INCENDIARY_GRENADE_ID, SpecialEquipmentRegistry.FIELD_MORTAR_ID)) {
            SpecialEquipmentDef special = SpecialEquipmentRegistry.get(id);
            if (special == null) {
                skipped.add("infantry " + id + ": not in the special equipment catalog");
                continue;
            }
            boolean mortar = id.equals(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
            boolean thrown = special.aiPolicy() == SpecialAiPolicy.SOFT_CLUSTER_INDIRECT;
            // The field mortar is set up as a structure, and the structure's tube is the weapon.
            WeaponDef def = mortar ? WeaponRegistry.require(FIELD_MORTAR_WEAPON_ID) : special.weaponDef();
            out.add(new Entry(Category.INFANTRY, id.substring("special.".length()), def.id, special.displayName(),
                    role(def), mode(def), mortar ? "rifle section with a field mortar"
                            : thrown ? "marine carrying it (throw scripted every 1.5 s)" : "marine carrying it",
                    def.fireSoundId, def.impactSoundId, def.loopSoundId, mortar ? 30f : thrown ? Math.min(def.range, 9f) : distance(def),
                    clipTicks(def),
                    (placement, sx, sy, tx, ty) -> {
                        SceneBuilder builder = placement.builder();
                        int lx = (int) sx - placement.site().x0();
                        int ly = (int) sy - placement.site().y0();
                        SceneBuilder.SquadSpec squad = builder.squad("shooter").faction(Faction.MARINE)
                                .size(mortar ? 4 : 1).at(lx, ly).primary("weapon.field-rifle")
                                .special(id, 1, 0).health(DUMMY_HEALTH);
                        if (mortar) {
                            // A tube goes down on a fire mission, as the field-mortar look stages it.
                            FireMission mission = new FireMission((int) tx, (int) ty, 4);
                            int ox = (int) sx;
                            int oy = (int) sy;
                            squad.assigned(squadId -> ObjectiveAssignment.attackMove(squadId, ox, oy)
                                    .withFireMission(mission));
                        } else {
                            squad.stationary();
                        }
                        // Only the special is heard: the carrier's primary is taken away and its reach cut.
                        squad.each(spec -> spec.primaryWeapon(null).attackRange(0.5f).attackDamage(0f));
                        squad.done();
                        if (!thrown) return withTarget(placement, builder, tx, ty, true, null);
                        // The AI reaches for a grenade or the flamer only in a fight its rifle is in, so with the
                        // rifle gone the throw is pulled on the dummies' middle as the Marine's right button pulls it.
                        float aimX = (int) tx - 2 + 0.5f;
                        float aimY = (int) ty + 0.5f;
                        return withTarget(placement, builder, tx, ty, true, world -> {
                            long carrier = world.members("shooter")[0];
                            int[] tick = {0};
                            return s -> {
                                if (tick[0]++ % THROW_EVERY_TICKS == THROW_FIRST_TICK && s.world().isAlive(carrier)
                                        && s.world().secondaryAmmo(carrier) > 0) {
                                    s.throwFragmentationGrenade(carrier, aimX, aimY);
                                }
                            };
                        });
                    }));
        }
        skipped.add("infantry special.point-defence-pack and special.point-defence-emplacement: they fire only at"
                + " incoming missiles, so a clip needs a launcher beside them; not staged");
        skipped.add("infantry special.satchel-charge and special.smoke-grenade: no weapon round (a placed charge, a"
                + " smoke screen)");
    }

    /**
     * Adds the target and builds: three unarmed militia dummies in a file two cells in front of a parked heavy APC,
     * both kept whole. Without {@code dummies} (an anti-armour rack, which holds its fire on a soft target its mech
     * has picked) the dummies stand behind the hull instead, so the hull is the nearest thing to shoot.
     * {@code spawn} adds the subject once the world stands, returning its own step, or null.
     */
    private static Staged withTarget(LookReview.Placement placement, SceneBuilder builder, float tx, float ty,
                                     boolean dummies,
                                     Function<SceneWorld, Consumer<BattleSimulation>> spawn) {
        int dx = dummies ? (int) tx - 2 : (int) tx + 7;
        int dy = (int) ty - 1;
        builder.squad("dummies").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3)
                .inFile(dx - placement.site().x0(), dy - placement.site().y0(), 0, 1)
                .unarmed().health(DUMMY_HEALTH).stationary()
                // An unarmed body still swings its archetype's baked attack: no reach, no harm, no sound.
                .each(spec -> spec.attackRange(0f).attackDamage(0f)).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        Set<Long> target = new HashSet<>();
        for (long id : world.members("dummies")) target.add(id);
        long truck = sim.convoy().spawn(new VehicleDeploymentSpec(null, null, TARGET_HULL),
                Faction.DEFENDER, VehicleMission.deployed(tx + 1.5f, ty));
        float facing = AirBody.facingToward(0f, 1f);
        sim.convoy().body(truck).teleport(tx + 1.5f, ty, facing);
        GroundTurret gun = sim.convoy().turret(truck);
        if (gun != null) {
            gun.ammo = 0;
            gun.unlimited = false;
            gun.loaderSeconds = Float.POSITIVE_INFINITY;
        }
        target.add(truck);
        // The company has eyes on the target from the start, so nothing waits on spotting it.
        sim.getFogOfWar().addEphemeralSource((int) tx, (int) ty, 8, 8f);
        Consumer<BattleSimulation> own = spawn != null ? spawn.apply(world) : null;
        Consumer<BattleSimulation> step = s -> {
            s.convoy().body(truck).teleport(tx + 1.5f, ty, facing);
            if (s.world().isAlive(truck)) s.world().setHp(truck, s.world().maxHp(truck));
            if (own != null) own.accept(s);
        };
        return new Staged(sim, step, target);
    }

    // ---- what a weapon is ----

    /** Far enough out of its minimum, near enough that the frame holds both ends of the shot. */
    static float distance(WeaponDef def) {
        float d = Math.min(def.range * 0.6f, 22f);
        d = Math.max(d, def.minRange + 4f);
        return Math.max(5f, Math.min(d, Math.max(def.range - 2f, 5f)));
    }

    /** Five seconds, six for a slow weapon whose second cycle comes late. */
    static int clipTicks(WeaponDef def) {
        return def.cooldown >= 4f || def.flightSec >= 1.2f ? 180 : 150;
    }

    /** How the trigger works, as the owner would say it. */
    static String mode(WeaponDef def) {
        if (def.loopSoundId != null) return "sustained beam (held)";
        if (def.rotary() != null) {
            return def.rotary().spinUpSeconds() > 0f ? "rotary, spins up (held trigger)" : "belt-fed (held trigger)";
        }
        if (def.pointDefenseTarget || def.interceptableProjectile) {
            return def.burstCount > 1 ? "salvo of " + def.burstCount : "single launch";
        }
        if (def.burstCount > 1) return "burst of " + def.burstCount;
        return "single shot";
    }

    private static String role(WeaponDef def) {
        String role = def.catalogRole();
        return role == null || role.startsWith("@") ? "" : role;
    }

    static String kebab(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
