package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.derelict.DerelictSecurityDirector;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.derelict.FacilityWake;
import com.dillon.starsectormarines.battle.derelict.SecurityZone;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A ruins site's light ({@link RuinsLighting}) through the look preset at 1920x1080: the plant dark at landing, with only
 * its emergency strips and the daylight through its open roof; partly lit, the hall restarted and its layer's junction
 * thrown to the store, so the hall and the store burn while the way on stays dark; and lit, every junction on the way
 * on. The power is routed through the works' own rules, not drawn; the roofs are off so the interior shows.
 * {@code -Dsnapshot.ruinsLight.seed} picks the site (default 31), {@code -Dsnapshot.ruinsLight.grade} the grade
 * (default EXTENSIVE), {@code -Dsnapshot.ruinsLight.focus} where the camera stands: {@code hall} (default), {@code entrance} (the first way in), {@code court} (the first light well inside) or
 * {@code works}, {@code -Dsnapshot.ruinsLight.roofed} the darkness under the roof, for a dimmer start to compare
 * (default {@link RuinsLighting#ROOFED_DARKNESS}), and {@code -Dsnapshot.ruinsLight.theme} a vault theme
 * ({@link RuinsVaultTheme}), so the site lays its works hall for the {@code works} focus, {@code -Dsnapshot.ruinsLight.watch=true} with
 * {@code -Dsnapshot.ruinsLight.occupant=DOMAIN_SECURITY} the watch's lights at its first zone's node, asleep and
 * hunting, {@code -Dsnapshot.ruinsLight.dy} moves the camera that many cells north (negative: south),
 * {@code -Dsnapshot.ruinsLight.company=true} stands a fire team, a Hound and an APC at
 * the focus with their own lights, dark and lit, and {@code -Dsnapshot.ruinsLight.warm=true} renders the hall's
 * restart instead, one renderer stepping the battle, so the lamps are seen striking as they warm up. {@code -Psnapshot=ruins-light-look}.
 */
public final class RuinsLightLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;

    @Override public String id() { return "ruins-light-look"; }

    @Override public String label() { return "Ruins light: the plant dark, partly lit and lit, look preset"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long seed = Long.getLong("snapshot.ruinsLight.seed", 31L);
        RuinsGrade grade = RuinsGrade.valueOf(System.getProperty("snapshot.ruinsLight.grade", "EXTENSIVE"));
        String focus = System.getProperty("snapshot.ruinsLight.focus", "hall");
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                grade, RuinsOccupant.valueOf(System.getProperty("snapshot.ruinsLight.occupant", "NOBODY")), prize())) {
            for (int t = 0; t < Math.round(5f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
            RuinsWorks works = sim.getRuinsWorks();
            String roofed = System.getProperty("snapshot.ruinsLight.roofed");
            NightLighting night = sim.getTopology().nightLighting();
            if (roofed != null && night != null) {
                sim.getTopology().setNightLighting(new NightLighting(night.darkness(), Float.parseFloat(roofed),
                        night.lamps(), night.poweredCircuits()));
            }
            RuinsDelve delve = works.delve();
            RuinsDelve.Cell stand = delve.interior() == null ? null : delve.interior().worksStand();
            float cx, cy;
            RuinsDelve.Area entrance = delve.interior() == null || delve.interior().entrances().isEmpty() ? null
                    : delve.interior().entrances().get(0);
            RuinsDelve.Court court = null;
            for (RuinsDelve.Layer layer : delve.layers()) {
                if (court == null && layer.court() != null && delve.interior() != null
                        && delve.interior().contains(layer.index())) court = layer.court();
            }
            if (focus.equals("works") && stand != null) {
                cx = stand.x();
                cy = stand.y();
            } else if (focus.equals("entrance") && entrance != null) {
                cx = entrance.centreX();
                cy = entrance.centreY();
            } else if (focus.equals("court") && court != null) {
                cx = court.ground().centreX();
                cy = court.ground().centreY();
            } else {
                cx = works.hall().x();
                cy = works.hall().y();
            }
            cy += Integer.getInteger("snapshot.ruinsLight.dy", 0);
            DerelictSecurityDirector watch = sim.derelictSecurity();
            if (Boolean.getBoolean("snapshot.ruinsLight.watch") && watch != null) {
                // The watch's own lights by its wake: asleep, then the hunt (every online zone alert, vents and bays up).
                SecurityZone zone = watch.network().zones().get(0);
                float wx = zone.nodeCellX, wy = zone.nodeCellY;
                out.add(frame(context, sim, wx, wy, "watch-1-quiet.png"));
                watch.wake().advance(FacilityWake.Stage.HUNTING);
                watch.network().alertAll();
                settle(sim);
                out.add(frame(context, sim, wx, wy, "watch-2-hunting.png"));
                works.graph().restart();
                settle(sim);
                out.add(frame(context, sim, wx, wy, "watch-3-hunting-plant-lit.png"));
                return out;
            }
            if (Boolean.getBoolean("snapshot.ruinsLight.company")) {
                // The company's own lights in the dark plant: a fire team, a Hound and an APC round the focus.
                List<long[]> company = standCompany(sim, cx, cy);
                out.add(frame(context, sim, cx, cy, "company-1-dark.png"));
                works.graph().restart();
                for (int j = 0; j < works.graph().junctionCount(); j++) works.graph().throwJunction(j, Branch.ONWARD);
                settle(sim);
                pose(sim, company);
                out.add(frame(context, sim, cx, cy, "company-2-lit.png"));
                return out;
            }
            if (Boolean.getBoolean("snapshot.ruinsLight.warm")) {
                // One renderer across the restart, the battle stepped, so the lamps strike and flicker as they warm up.
                BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy,
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs();
                out.add(new SnapshotArtifact("warm-before.png", renderer.render(sim, "")));
                works.graph().restart();
                float elapsed = 0f;
                for (float t : new float[] {0.1f, 0.4f, 0.9f, 1.4f, 2.5f}) {
                    for (; elapsed < t; elapsed += BattleSimulation.TICK_DT) sim.advance(BattleSimulation.TICK_DT);
                    out.add(new SnapshotArtifact(String.format("warm-%.1fs.png", t), renderer.render(sim, "")));
                }
                return out;
            }
            out.add(frame(context, sim, cx, cy, "01-dark.png"));
            works.graph().restart();
            works.graph().throwJunction(delve.powerHall().layer(), Branch.STORE);
            settle(sim);
            out.add(frame(context, sim, cx, cy, "02-partly-lit.png"));
            for (int j = 0; j < works.graph().junctionCount(); j++) works.graph().throwJunction(j, Branch.ONWARD);
            settle(sim);
            out.add(frame(context, sim, cx, cy, "03-lit.png"));
        }
        return out;
    }

    /** The vault's prize from {@code -Dsnapshot.ruinsLight.theme}, so the site lays that theme's works hall; null for none. */
    private static RuinsVaultPrize prize() {
        String name = System.getProperty("snapshot.ruinsLight.theme");
        if (name == null) return null;
        RuinsVaultTheme theme = RuinsVaultTheme.valueOf(name);
        return new RuinsVaultPrize(theme.family, theme,
                theme.family == RuinsVaultPrize.Family.AI_CORE ? RuinsVaultPrize.BETA_CORE : theme.itemId, 2);
    }

    /**
     * Stands a fire team, a Hound and an APC of the company round {@code (cx, cy)}, each facing its own way so its cone
     * reads; {entity, x, y, facing, kind} each, kind 0 a marine, 1 the mech, 2 the vehicle.
     */
    private static List<long[]> standCompany(BattleSimulation sim, float cx, float cy) {
        List<long[]> out = new ArrayList<>();
        int[][] team = {{10, -1, 100}, {10, -3, 80}, {11, -2, 90}, {11, -4, 120}};
        for (int[] m : team) {
            int[] at = open(sim, (int) cx + m[0], (int) cy + m[1], 0);
            if (at == null) continue;
            long id = sim.spawn(new EntitySpec("Lamp " + out.size(), Faction.MARINE, UnitType.MARINE, at[0], at[1]));
            out.add(new long[] {id, at[0], at[1], m[2], 0});
        }
        int[] hound = open(sim, (int) cx + 4, (int) cy + 5, 2);
        if (hound != null) {
            int squad = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
            long mech = sim.spawn(new EntitySpec("Lamp Hound", Faction.MARINE, UnitType.HEAVY_MECH, hound[0], hound[1])
                    .atPosition(hound[0] + 0.5f, hound[1] + 0.5f).mechVariant(MechVariant.HOUND).squad(squad));
            sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(MechVariant.HOUND,
                    MechVariant.HOUND.defaultRole, "independent"));
            Squad s = sim.getSquad(squad);
            s.leaderId = mech;
            s.originalSize = 1;
            out.add(new long[] {mech, hound[0], hound[1], 0, 1});
        }
        int[] apc = open(sim, (int) cx + 5, (int) cy - 6, 3);
        if (apc != null) {
            long id = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE, VehicleMission.deployed(apc[0] + 0.5f,
                    apc[1] + 0.5f));
            out.add(new long[] {id, apc[0], apc[1], 45, 2});
        }
        pose(sim, out);
        return out;
    }

    /** Puts each of the company back where it was stood, facing as it was. */
    private static void pose(BattleSimulation sim, List<long[]> company) {
        BattleComponents c = sim.getBattleComponents();
        for (long[] u : company) {
            float x = u[1] + 0.5f, y = u[2] + 0.5f, facing = u[3];
            if (u[4] == 2) {
                sim.convoy().body(u[0]).teleport(x, y, facing);
                continue;
            }
            sim.getEntityWorld().setFloat(u[0], c.POSITION, BattleComponents.POSITION_X, x);
            sim.getEntityWorld().setFloat(u[0], c.POSITION, BattleComponents.POSITION_Y, y);
            if (u[4] == 1) {
                FacingSystem.faceStanding(sim.getEntityWorld(), c, u[0], facing);
            } else {
                sim.getEntityWorld().setFloat(u[0], c.LAYERED_ANIMATION, BattleComponents.LAYERED_FACING_DEGREES, facing);
            }
        }
    }

    /** The walkable cell nearest {@code (x, y)} with {@code clear} walkable cells all round it, within six; null for none. */
    private static int[] open(BattleSimulation sim, int x, int y, int clear) {
        for (int r = 0; r <= 6; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (clearAround(sim, x + dx, y + dy, clear)) return new int[] {x + dx, y + dy};
                }
        return null;
    }

    private static boolean clearAround(BattleSimulation sim, int x, int y, int clear) {
        for (int dy = -clear; dy <= clear; dy++)
            for (int dx = -clear; dx <= clear; dx++)
                if (!sim.getGrid().inBounds(x + dx, y + dy) || !sim.getGrid().isWalkable(x + dx, y + dy)) return false;
        return true;
    }

    /** Runs the battle on three seconds, long enough for a circuit just powered to warm up. */
    private static void settle(BattleSimulation sim) {
        for (int t = 0; t < Math.round(3f / BattleSimulation.TICK_DT); t++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, float cx, float cy,
                                          String name) throws Exception {
        return new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy,
                BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, ""));
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
