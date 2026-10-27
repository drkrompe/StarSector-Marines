package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.SalvoChain;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;

import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimLane;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlAimOrigins;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlPanel;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlScatter;
import com.dillon.starsectormarines.battle.warship.VanillaData;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import com.dillon.starsectormarines.battle.warship.WarshipPilot;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.HullTurretSection;
import com.dillon.starsectormarines.battle.vehicle.MountedGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleFireGroups;
import com.dillon.starsectormarines.battle.vehicle.VehicleMount;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.control.DirectControlAbility;
import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import java.awt.BasicStroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import javax.imageio.ImageIO;
import java.util.function.Consumer;
import java.awt.geom.Line2D;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Production control HUD and composed host overlays, without a battle simulation or live host. */
public final class DirectControlUiSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "direct-control-ui"; }
    @Override public String label() { return "Direct-control HUD"; }

    private static BufferedImage aimLane(String scenario) {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(24, 30, 35)); graphics.fillRect(0, 0, 640, 360);
        NavigationGrid grid = new NavigationGrid(16, 9);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 9; y++) {
            grid.setWalkable(x, y, true);
            graphics.setColor(new Color(40, 47, 50)); graphics.drawRect(x * 40, y * 40, 40, 40);
        }
        if (scenario.equals("corner") || scenario.equals("closed-edge")) {
            int wy = scenario.equals("corner") ? 4 : 3;
            grid.setWalkable(7, wy, false);
            graphics.setColor(new Color(94, 99, 103)); graphics.fillRect(280, wy * 40, 40, 40);
        }
        float aimY = scenario.equals("corner") ? 7.5f : 3.5f;
        if (!scenario.equals("chrome-hidden")) {
            var loadout = new MechLoadoutComponent(MechVariant.BULWARK, null);
            loadout.torsoFacingDegrees = 90f;
            var origins = scenario.equals("carrier-muzzles")
                    ? DirectControlAimOrigins.mech(loadout, TriggerPicks.LINKED, 2.5f, 3.5f, .2f, -.1f)
                    : List.of(new DirectControlAimOrigins.Origin(2.5f, 3.5f, 2.5f, 3.5f));
            for (var origin : origins) {
                var lane = DirectControlAimLane.observed(grid, origin, 13.5f, aimY, (x, y) -> true);
                for (var stroke : DirectControlAimLane.strokes(lane, 40, 0, 0)) {
                    graphics.setColor(stroke.color()); graphics.setStroke(new BasicStroke(stroke.width()));
                    graphics.draw(new Line2D.Float(stroke.x(), stroke.y(), stroke.endX(), stroke.endY()));
                }
            }
        }
        graphics.setColor(new Color(110, 215, 255)); graphics.drawRect(86, 126, 28, 28);
        graphics.dispose(); return image;
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(context.modRoot().resolve(path)));
        try {
            return renderAll(context);
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    private List<SnapshotArtifact> renderAll(SnapshotContext context) throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (String scenario : List.of("clear", "corner", "closed-edge", "chrome-hidden", "carrier-muzzles")) {
            artifacts.add(new SnapshotArtifact("aim-lane-" + scenario + ".png", aimLane(scenario)));
        }
        try (var markup = BattleDirectControlOverlayTest.fixture(
                context.modRoot(), false, true, () -> {})) {
            var document = BattleDirectControlOverlayTest.document(markup);
            artifacts.add(new SnapshotArtifact("ready.png", renderer.render(document,
                    (int) BattleDirectControlOverlay.DOCUMENT_WIDTH,
                    (int) BattleDirectControlOverlay.DOCUMENT_HEIGHT)));
        }
        for (var carrier : List.of(BattleDirectControlStatus.Carrier.MARINE,
                BattleDirectControlStatus.Carrier.MECH, BattleDirectControlStatus.Carrier.VEHICLE)) {
            var snapshot = carrier == BattleDirectControlStatus.Carrier.MECH
                    ? BattleDirectControlHudTest.triggered(0) : BattleDirectControlHudTest.preview(carrier, false);
            try (var fixture = BattleDirectControlHudTest.fixture(context.modRoot(), snapshot, 0, false,
                    ignored -> {}, () -> {}, () -> {})) {
                artifacts.add(new SnapshotArtifact("panels-" + carrier.name().toLowerCase(Locale.ROOT)
                        + ".png", panels(renderer, fixture)));
            }
        }
        for (int state = 0; state < 4; state++) {
            try (var fixture = BattleDirectControlHudTest.fixture(context.modRoot(),
                    BattleDirectControlHudTest.abilityPreview(state), 0, false,
                    ignored -> {}, () -> {}, () -> {})) {
                String phase = new String[]{"full", "running", "empty", "recovering"}[state];
                artifacts.add(new SnapshotArtifact("panels-abilities-sprint-" + phase + ".png", panels(renderer, fixture)));
            }
        }
        try (var fixture = BattleDirectControlHudTest.fixture(context.modRoot(),
                BattleDirectControlHudTest.bulwark(new TriggerPicks(MechMountSlot.RIGHT_ARM.ordinal() + 1,
                        MechFireGroups.LINKED)), 0, true,
                ignored -> {}, () -> {}, () -> {})) {
            artifacts.add(new SnapshotArtifact("panels-mech-one-mount.png", panels(renderer, fixture)));
            artifacts.add(new SnapshotArtifact("action-controls.png", renderer.render(fixture.actionsDocument(),
                    (int) BattleDirectControlOverlay.ACTIONS_WIDTH, (int) BattleDirectControlOverlay.ACTIONS_HEIGHT)));
        }
        artifacts.add(new SnapshotArtifact("action-help-warship-expanded.png",
                helpFrame(renderer, context.modRoot(), BattleDirectControlStatus.Carrier.WARSHIP)));
        artifacts.add(new SnapshotArtifact("action-help-collapsed.png",
                helpFrame(renderer, context.modRoot(), null)));
        artifacts.add(new SnapshotArtifact("selected-mech-1920x1080-ui100.png",
                composite(renderer, context.modRoot(), 1920, 1080, 1f, true)));
        artifacts.add(new SnapshotArtifact("selected-infantry-1920x1080-ui100.png",
                composite(renderer, context.modRoot(), 1920, 1080, 1f, false)));
        artifacts.add(new SnapshotArtifact("active-mech-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, false)));
        artifacts.add(new SnapshotArtifact("active-mech-paused-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, true)));
        artifacts.add(new SnapshotArtifact("active-apc-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f,
                        BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.VEHICLE, false),
                        vehiclePlate(context.modRoot()).document(), false)));
        FlankedTank tank = flankedTank(context.modRoot());
        artifacts.add(new SnapshotArtifact("active-tank-flanked-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, tank.status(), tank.plate().document(), false)));
        artifacts.add(new SnapshotArtifact("mech-sections-states-1920x1080-ui100.png",
                sectionStates(renderer, context.modRoot())));
        for (MagazineFrame frame : MAGAZINE_FRAMES) {
            artifacts.add(new SnapshotArtifact("active-marine-magazine-" + frame.name() + "-1920x1080-ui100.png",
                    activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, frame.snapshot(),
                            marinePlate(context.modRoot(), true).document(), false)));
        }
        var marine = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MARINE, false);
        artifacts.add(new SnapshotArtifact("active-marine-doll-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, marine,
                        marinePlate(context.modRoot(), true).document(), false)));
        artifacts.add(new SnapshotArtifact("active-marine-doll-figure-only-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, marine,
                        marinePlate(context.modRoot(), false).document(), false)));
        artifacts.add(new SnapshotArtifact("active-marine-flamer-1920x1080-ui100.png",
                MarineLeftSpecialSnapshotSuite.flamerFrame(renderer, context)));
        artifacts.add(new SnapshotArtifact("active-marine-wide-status-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, marine, null, false, true)));
        artifacts.add(new SnapshotArtifact("active-mech-ordnance-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, ordnanceMech(),
                        sectionPlate(context.modRoot(), new MechLoadoutComponent(MechVariant.BULWARK, null)).document(),
                        false)));
        artifacts.add(new SnapshotArtifact("active-mech-chaingun-belt-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, chaingunBelts(),
                        sectionPlate(context.modRoot(), new MechLoadoutComponent(MechVariant.BULWARK, null)).document(),
                        false)));
        // A held LRM-15 building its spike: each salvo the hold commits lights up in the strip (mechs-nouns.md).
        for (int salvos = 1; salvos <= MechWeaponComponent.LRM_15.ammoCapacity; salvos++) {
            artifacts.add(new SnapshotArtifact("active-mech-lrm-hold-" + salvos + "-1920x1080-ui100.png",
                    activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, holdingMech(salvos),
                            sectionPlate(context.modRoot(), new MechLoadoutComponent(MechVariant.BULWARK, null))
                                    .document(), false)));
        }
        artifacts.add(new SnapshotArtifact("active-vehicle-ordnance-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, ordnanceVehicle(),
                        vehiclePlate(context.modRoot()).document(), false)));
        // A hull with a second gun: the cannon on the left button and the coax MG on the right (FireGroups).
        artifacts.add(new SnapshotArtifact("active-vehicle-two-guns-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, twoGunTank(),
                        tankPlate(context.modRoot()).document(), false)));
        for (LauncherAim aim : LAUNCHER_AIMS) {
            artifacts.add(new SnapshotArtifact("active-vehicle-lrm-" + aim.name() + "-1920x1080-ui100.png",
                    launcherFrame(renderer, context.modRoot(), aim)));
        }
        artifacts.add(new SnapshotArtifact("active-warship-1920x1080-ui100.png",
                warshipFrame(renderer, context.modRoot(), context.starsectorCore())));
        artifacts.add(new SnapshotArtifact("active-marine-pulse-rifle-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, pulseRifle(),
                        marinePlate(context.modRoot(), true).document(), false)));
        // One Marine carrying smoke in hand, a frag on the belt and a shield: every RMB item, the frag picked,
        // and all three in the doll's kit column.
        artifacts.add(new SnapshotArtifact("active-marine-smoke-and-grenade-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f, smokeAndGrenade(),
                        smokeAndGrenadePlate(context.modRoot()).document(), false)));
        artifacts.add(new SnapshotArtifact("marine-doll-states-1920x1080-ui100.png",
                marineStates(renderer, context.modRoot())));
        // A Bulwark's trigger headers on LINKED, then with one arm gun and one rack picked.
        var oneGun = new TriggerPicks(MechMountSlot.RIGHT_ARM.ordinal() + 1, MechMountSlot.LEFT_SHOULDER.ordinal() + 1);
        artifacts.add(new SnapshotArtifact("pick-linked-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f,
                        BattleDirectControlHudTest.bulwark(TriggerPicks.LINKED),
                        sectionPlate(context.modRoot(), new MechLoadoutComponent(MechVariant.BULWARK, null)).document(),
                        false)));
        artifacts.add(new SnapshotArtifact("pick-one-mount-1920x1080-ui100.png",
                activeComposite(renderer, context.modRoot(), 1920, 1080, 1f,
                        BattleDirectControlHudTest.bulwark(oneGun),
                        sectionPlate(context.modRoot(), new MechLoadoutComponent(MechVariant.BULWARK, null)).document(),
                        false)));
        return artifacts;
    }

    /** A controlled Marine's magazine at one moment: the real status row a primary would publish. */
    private record MagazineFrame(String name, String weaponId, EquipmentGrade grade, int rounds,
                                 float cooldown, float reload) {
        BattleDirectControlStatus.Snapshot snapshot() {
            WeaponDef weapon = WeaponRegistry.require(weaponId);
            float cycle = InfantryCombatStats.cooldown(weapon, grade, SoldierProfile.REGULAR);
            var status = BattleDirectControlStatus.marineWeapon(weapon, grade, cooldown, cycle, 0, 0f, rounds,
                    reload, InfantryCombatStats.reload(weapon, grade, SoldierProfile.REGULAR));
            var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MARINE, false);
            return new BattleDirectControlStatus.Snapshot(base.entityId(), base.name(), base.carrier(),
                    base.durability(), List.of(status), List.of(), MarineSprint.EMPTY);
        }
    }

    private static final List<MagazineFrame> MAGAZINE_FRAMES = List.of(
            new MagazineFrame("rook-full", "weapon.field-rifle", EquipmentGrade.SERVICE, 4, 0f, 0f),
            new MagazineFrame("rook-cycling", "weapon.field-rifle", EquipmentGrade.SERVICE, 1, .2f, 0f),
            new MagazineFrame("rook-reloading", "weapon.field-rifle", EquipmentGrade.SERVICE, 0, 0f, 1.8f),
            new MagazineFrame("stalwart-belt", "weapon.squad-automatic", EquipmentGrade.MILSPEC, 27, 0f, 0f),
            new MagazineFrame("carbine-shells", "weapon.smg", EquipmentGrade.SERVICE, 3, 0f, 0f),
            new MagazineFrame("longbow-masterwork", "weapon.dmr", EquipmentGrade.MASTERWORK, 10, 0f, 0f));

    /**
     * A mech mid-fight on missile and energy mounts: an LRM-15 one salvo down, an SRM-5 part way
     * through a salvo, a pulse laser two bolts into its burst and a chaingun on a full belt.
     */
    private static BattleDirectControlStatus.Snapshot ordnanceMech() {
        MechWeaponMount[] mounts = {
                mount(MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.LRM_15, 2, 0),
                mount(MechMountSlot.RIGHT_SHOULDER, MechWeaponComponent.SRM_5, 4, 1),
                mount(MechMountSlot.LEFT_ARM, MechWeaponComponent.PULSE_LASER, -1, 2),
                mount(MechMountSlot.RIGHT_ARM, MechWeaponComponent.CHAINGUN, -1, 0)};
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MECH, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), "Bulwark Lead",
                BattleDirectControlStatus.Carrier.MECH, base.durability(),
                BattleDirectControlStatus.mechWeapons(mounts, new MechFlux()));
    }

    /**
     * A Bulwark's two chainguns mid-fight, played through the feed's own rule rather than set: the left gun
     * half spun up again on a belt it has already run down by half, the right gun at full speed on a belt
     * held long enough to run low (the hopper keeps it from empty).
     */
    private static BattleDirectControlStatus.Snapshot chaingunBelts() {
        MechWeaponMount left = new MechWeaponMount(MechMountSlot.LEFT_ARM, MechWeaponComponent.CHAINGUN);
        MechWeaponMount right = new MechWeaponMount(MechMountSlot.RIGHT_ARM, MechWeaponComponent.CHAINGUN);
        hold(left, true, 10f);
        hold(left, false, 3f);
        hold(left, true, 0.6f);
        hold(right, true, 18f);
        MechWeaponMount[] mounts = {left, right,
                new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_15),
                new MechWeaponMount(MechMountSlot.RIGHT_SHOULDER, MechWeaponComponent.SRM_15)};
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MECH, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), "Bulwark Lead",
                BattleDirectControlStatus.Carrier.MECH, base.durability(),
                BattleDirectControlStatus.mechWeapons(mounts, new MechFlux()));
    }

    /** Holds (or releases) a rotary mount's trigger for {@code seconds}, every round it asks for fired. */
    private static void hold(MechWeaponMount mount, boolean held, float seconds) {
        for (int i = 0; i < Math.round(seconds / BattleSimulation.TICK_DT); i++) {
            int rounds = mount.rotary.advance(BattleSimulation.TICK_DT, held);
            for (int r = 0; r < rounds; r++) mount.rotary.spend();
        }
    }

    /**
     * A Bulwark holding its right button on an LRM-15 with {@code salvos} of its three committed: the salvo in flight
     * is part way out, and every salvo the hold has fired draws lit beside the ones still loaded.
     */
    private static BattleDirectControlStatus.Snapshot holdingMech(int salvos) {
        MechWeaponMount lrm = mount(MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.LRM_15,
                MechWeaponComponent.LRM_15.ammoCapacity - salvos, 2);
        lrm.holdSalvos = salvos;
        lrm.cycleRemaining = lrm.component.cycleSeconds() - .2f;
        MechWeaponMount[] mounts = {
                lrm,
                mount(MechMountSlot.RIGHT_SHOULDER, MechWeaponComponent.SRM_5, 6, 0),
                mount(MechMountSlot.LEFT_ARM, MechWeaponComponent.CHAINGUN, -1, 0),
                mount(MechMountSlot.RIGHT_ARM, MechWeaponComponent.CHAINGUN, -1, 0)};
        MechFlux flux = new MechFlux();
        flux.commit(SalvoChain.fluxCost(lrm.component.fluxPerTrigger(), false)
                + (salvos - 1) * SalvoChain.fluxCost(lrm.component.fluxPerTrigger(), true));
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MECH, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), "Bulwark Lead",
                BattleDirectControlStatus.Carrier.MECH, base.durability(),
                BattleDirectControlStatus.mechWeapons(mounts, flux), List.of(), MarineSprint.EMPTY,
                new BattleDirectControlStatus.MechFluxStatus(flux.value(), flux.venting(), flux.capacity()));
    }

    private static MechWeaponMount mount(MechMountSlot slot, MechWeaponComponent component, int ammo, int burst) {
        var mount = new MechWeaponMount(slot, component);
        if (ammo >= 0) mount.ammo = ammo;
        mount.burstRemaining = burst;
        return mount;
    }

    /** Vehicle turrets on each long load: an LRM launcher in salvos, a heavy MG's belt, a burst laser, a pulse emitter. */
    private static BattleDirectControlStatus.Snapshot ordnanceVehicle() {
        List<BattleDirectControlStatus.WeaponStatus> weapons = new ArrayList<>();
        String[][] turrets = {{"mount.vehicle-lrm", "37", "0"}, {"mount.turret-heavy-mg", "131", "0"},
                {"mount.tank-burst-laser", "420", "2"}, {"mount.hover-pulse", "160", "0"}};
        for (String[] turret : turrets) {
            var mount = TurretCatalogRegistry.requireMount(turret[0]);
            var ground = new GroundTurret(Integer.parseInt(turret[1]));
            ground.burstRemaining = Integer.parseInt(turret[2]);
            var status = BattleDirectControlStatus.vehicleWeapon(ground, mount);
            weapons.add(new BattleDirectControlStatus.WeaponStatus(weapons.size() + 1, status.slotKey() + weapons.size(),
                    status.name(), status.behavior(), status.directEligible(), status.cooldownSeconds(),
                    status.cycleDurationSeconds(), status.burstRemaining(), status.burstSeconds(), status.ammo()));
        }
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.VEHICLE, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), "Breakthrough Tank",
                BattleDirectControlStatus.Carrier.VEHICLE, base.durability(), weapons);
    }

    /** A coax heavy MG beside a tank's cannon: a second mount, the way a hull declares one. */
    private static final VehicleMount COAX = new VehicleMount(TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID,
            .22f, .35f, false);

    /**
     * A breakthrough tank fitted with a coax heavy MG, its rows built by the production status path from the
     * production sorter: the cannon leads the left button and the MG is the right button's, partly spent.
     */
    private static BattleDirectControlStatus.Snapshot twoGunTank() {
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        var cannon = type.turretStructure().mount;
        var turret = new GroundTurret(cannon.ammoCapacity);
        turret.secondaries.add(MountedGun.of(COAX));
        var coax = turret.secondaries.get(0);
        coax.turret().ammo = coax.mount().structure().mount.ammoCapacity * 2 / 3;
        var mounts = VehicleFireGroups.mounts(type, turret);
        var picks = TriggerPicks.LINKED;
        List<BattleDirectControlStatus.WeaponStatus> weapons = List.of(
                BattleDirectControlStatus.vehicleWeapon(turret, cannon, Float.NaN, 1, "TURRET",
                        FireGroups.badge(mounts, picks, 0), FireGroups.group(mounts, 0)),
                BattleDirectControlStatus.vehicleWeapon(coax.turret(), coax.mount().structure().mount, Float.NaN, 2,
                        "MOUNT", FireGroups.badge(mounts, picks, 1), FireGroups.group(mounts, 1)));
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.VEHICLE, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), "Breakthrough Tank",
                BattleDirectControlStatus.Carrier.VEHICLE, base.durability(), weapons);
    }

    /** The LRM carrier's cursor {@code cells} from its turret ring, along a bearing up and to the right. */
    private record LauncherAim(String name, float cells) { }

    /** Inside range, past it (where the disk grows faster), and inside the minimum, where the trigger is refused. */
    private static final List<LauncherAim> LAUNCHER_AIMS = List.of(
            new LauncherAim("in-range", 24f), new LauncherAim("beyond-range", 46f),
            new LauncherAim("too-close", 5f));

    /** Pixels per cell for the launcher frames' world. */
    private static final float LAUNCHER_CELL_PX = 24f;

    /**
     * The controlled LRM carrier with its cursor reticle, and the HUD's launcher row for the same cursor. The tick's
     * spread is {@link DirectControlScatter#vehicle} and the strokes {@link DirectControlReticle}'s, the production
     * figures; the world beneath is a plain grid, and the strokes are drawn through Java2D rather than the panel's GL.
     */
    private static BufferedImage launcherFrame(HeadlessUiRenderer renderer, Path modRoot, LauncherAim aim)
            throws Exception {
        VehicleType type = VehicleType.LRM_CARRIER;
        var mount = type.turretStructure().mount;
        float spread = DirectControlScatter.vehicle(mount.weapon, aim.cells());
        var status = BattleDirectControlStatus.vehicleWeapon(new GroundTurret(mount.ammoCapacity), mount, aim.cells());
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.VEHICLE, false);
        var snapshot = new BattleDirectControlStatus.Snapshot(base.entityId(), "LRM Carrier",
                BattleDirectControlStatus.Carrier.VEHICLE, base.durability(), List.of(status));
        var plate = new BattleDamageDollPlate(path -> Files.readString(modRoot.resolve(path)),
                ModStrings.fromDisk(modRoot));
        plate.updateVehicle(type, type.maxStructure, type.maxStructure, null, HullLocations.of(type, null));
        BufferedImage sheet = ImageIO.read(modRoot.resolve(type.spritePath).toFile());
        return activeComposite(renderer, modRoot, 1920, 1080, 1f, snapshot, plate.document(), false, false,
                graphics -> drawLauncherWorld(graphics, sheet, type, spread, aim.cells()));
    }

    /** Pixels per cell for the warship frame's world. */
    private static final float WARSHIP_CELL_PX = 9f;

    /**
     * A piloted Enforcer with both batteries laid, the real ship strip beside it. The camera is leashed to the hull,
     * so nothing outlines it. The rings' colours are {@link DirectControlPanel#laidAreaColor}; the strokes are Java2D
     * copies of the panel's GL rings and crosshair over a plain grid. The hull and its sprite come from the game
     * install.
     */
    private static BufferedImage warshipFrame(HeadlessUiRenderer renderer, Path modRoot, Path starsectorCore)
            throws Exception {
        InstalledHullSpecs.install(starsectorCore);
        InstalledVanillaData.install(starsectorCore);
        try {
            Warship ship = Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, 0f, 0f, 0f);
            ship.takeHelm();
            float r = WarshipPilot.AREA_RADIUS_CELLS;
            var guns = new WarshipOrder.FireOnArea(-30f, -44f, r, true);
            var missiles = new WarshipOrder.FireOnArea(34f, -36f, r, true);
            ship.layArea(Warship.FireGroup.GUNS, guns);
            ship.layArea(Warship.FireGroup.MISSILES, missiles);
            BufferedImage sprite = ImageIO.read(starsectorCore.resolve(ship.spritePath).toFile());
            return activeComposite(renderer, modRoot, 1920, 1080, 1f,
                    BattleDirectWarshipStatus.snapshot(ship, false, false), null, false, false,
                    graphics -> drawWarshipWorld(graphics, sprite, ship, guns, missiles));
        } finally {
            VanillaData.useSource(null);
        }
    }

    /** Ship-relative cells to screen: the hull sits a little below centre, bow up. */
    private static float warshipScreenX(float cells) { return 900f + cells * WARSHIP_CELL_PX; }
    private static float warshipScreenY(float cells) { return 720f + cells * WARSHIP_CELL_PX; }

    private static void drawWarshipWorld(Graphics2D graphics, BufferedImage sprite, Warship ship,
                                         WarshipOrder.FireOnArea guns, WarshipOrder.FireOnArea missiles) {
        float px = WARSHIP_CELL_PX;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = ship.lengthCells * px / sprite.getHeight();
        AffineTransform saved = graphics.getTransform();
        graphics.translate(warshipScreenX(0f), warshipScreenY(0f));
        graphics.scale(scale, scale);
        graphics.drawImage(sprite, -sprite.getWidth() / 2, -sprite.getHeight() / 2, null);
        graphics.setTransform(saved);
        for (var laid : new Object[][]{{guns, Warship.FireGroup.GUNS}, {missiles, Warship.FireGroup.MISSILES}}) {
            var area = (WarshipOrder.FireOnArea) laid[0];
            Color c = DirectControlPanel.laidAreaColor((Warship.FireGroup) laid[1]);
            float cx = warshipScreenX(area.x());
            float cy = warshipScreenY(area.y());
            float r = area.radius() * px;
            graphics.setColor(c);
            graphics.setStroke(new BasicStroke(2f));
            graphics.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
            graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * 0.45f)));
            graphics.setStroke(new BasicStroke(1f));
            graphics.draw(new Ellipse2D.Float(cx - r * 0.5f, cy - r * 0.5f, r, r));
            graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * 0.8f)));
            crosshair(graphics, cx, cy);
        }
        graphics.setColor(DirectControlPanel.controlledColor());
        crosshair(graphics, warshipScreenX(6f), warshipScreenY(-58f));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    /** The panel's four-arm crosshair, in whatever colour the graphics holds. */
    private static void crosshair(Graphics2D graphics, float x, float y) {
        graphics.fillRect(Math.round(x - 8f), Math.round(y - 1f), 5, 2);
        graphics.fillRect(Math.round(x + 3f), Math.round(y - 1f), 5, 2);
        graphics.fillRect(Math.round(x - 1f), Math.round(y - 8f), 2, 5);
        graphics.fillRect(Math.round(x - 1f), Math.round(y + 3f), 2, 5);
    }

    private static void drawLauncherWorld(Graphics2D graphics, BufferedImage sheet, VehicleType type,
                                          float spreadCells, float cells) {
        float px = LAUNCHER_CELL_PX;
        double bearing = Math.atan2(-470.0, 1000.0);
        float ringX = 420f;
        float ringY = 800f;
        float aimX = ringX + (float) (Math.cos(bearing) * cells * px);
        float aimY = ringY + (float) (Math.sin(bearing) * cells * px);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // The sheet's four frames draw the nose to the left; each frame is drawn the type's drawn length long.
        int frame = sheet.getWidth() / 4;
        double scale = type.drawnLengthCells * px / frame;
        AffineTransform saved = graphics.getTransform();
        graphics.translate(ringX, ringY);
        graphics.rotate(bearing + Math.PI);
        graphics.translate(type.turretMountX * px, 0);
        graphics.scale(scale, scale);
        graphics.drawImage(sheet.getSubimage(0, 0, frame, sheet.getHeight()), -frame / 2, -sheet.getHeight() / 2, null);
        graphics.drawImage(sheet.getSubimage(frame, 0, frame, sheet.getHeight()), -frame / 2, -sheet.getHeight() / 2,
                null);
        graphics.setTransform(saved);
        List<DirectControlAimLane.Stroke> strokes = new ArrayList<>();
        WeaponDef weapon = type.turretStructure().mount.weapon;
        var marks = new DirectControlFireMarks.Marks(DirectControlFireMarks.of(!ManualPointFire.tooClose(weapon, cells),
                cells, ManualPointFire.optimalRangeCells(weapon), weapon.range), DirectControlFireMarks.Mark.NONE);
        // The reticle is laid out y up, as the GL panel draws; this frame is y down, so build it mirrored and flip back.
        for (var stroke : DirectControlReticle.strokes(marks, spreadCells * px, -1f,
                aimX, -aimY, aimX - ringX, ringY - aimY, DirectControlPanel.controlledColor())) {
            strokes.add(new DirectControlAimLane.Stroke(stroke.x(), -stroke.y(), stroke.endX(), -stroke.endY(),
                    stroke.width(), stroke.color()));
        }
        for (var stroke : strokes) {
            Color c = stroke.color();
            graphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * 0.8f)));
            graphics.setStroke(new BasicStroke(stroke.width()));
            graphics.draw(new Line2D.Float(stroke.x(), stroke.y(), stroke.endX(), stroke.endY()));
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    /** A Marine on the pulse rifle, one bolt into its three-bolt burst. */
    private static BattleDirectControlStatus.Snapshot pulseRifle() {
        WeaponDef weapon = WeaponRegistry.require("weapon.pulse-rifle");
        float cycle = InfantryCombatStats.cooldown(weapon, EquipmentGrade.SERVICE, SoldierProfile.REGULAR);
        var status = BattleDirectControlStatus.marineWeapon(weapon, EquipmentGrade.SERVICE, cycle * .5f, cycle,
                2, weapon.burstSpacing);
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MARINE, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), base.name(), base.carrier(),
                base.durability(), List.of(status), List.of(), MarineSprint.EMPTY);
    }

    /** A rifleman with a smoke grenade, a frag in the second special slot and a breaching assist's shield: the frag picked. */
    private static BattleDirectControlStatus.Snapshot smokeAndGrenade() {
        var smoke = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        var frag = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID);
        WeaponDef weapon = WeaponRegistry.require("weapon.field-rifle");
        float cycle = InfantryCombatStats.cooldown(weapon, EquipmentGrade.SERVICE, SoldierProfile.REGULAR);
        var rifle = BattleDirectControlStatus.marineWeapon(weapon, EquipmentGrade.SERVICE, 0f, cycle, 0, 0f);
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.MARINE, false);
        return new BattleDirectControlStatus.Snapshot(base.entityId(), base.name(), base.carrier(),
                base.durability(), List.of(rifle), List.of(
                        BattleDirectControlStatus.smokeAbility(smoke, smoke.startingAmmo(), null, 0f, true),
                        BattleDirectControlStatus.grenadeAbility(frag, frag.startingAmmo() - 1, false, 0f, true)
                                .withPicked(true),
                        new BattleDirectControlStatus.AbilityStatus(DirectControlAbility.SHIELD, "Breaching assist",
                                true, 0f, 6f, 0f, 12f, -1, -1, 0f, 120f, false, 90f, 120f)),
                MarineSprint.EMPTY);
    }

    private static BattleMarineDollPlate smokeAndGrenadePlate(Path modRoot) {
        var smoke = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        var frag = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID);
        var copy = ModStrings.fromDisk(modRoot);
        var plate = new BattleMarineDollPlate(path -> Files.readString(modRoot.resolve(path)), copy, true);
        plate.update(new BattleMarineDollPlate.MarineDoll(false, true, 35f / 60f, .74f, 0, 0, List.of(
                new BattleMarineDollPlate.Kit(smoke.displayName(),
                        smoke.startingAmmo() + "/" + smoke.startingAmmo(), false,
                        BattleMarineDollPlate.Kit.round(smoke), smoke.startingAmmo(), smoke.startingAmmo()),
                new BattleMarineDollPlate.Kit(frag.displayName(),
                        (frag.startingAmmo() - 1) + "/" + frag.startingAmmo(), false,
                        BattleMarineDollPlate.Kit.round(frag), frag.startingAmmo() - 1, frag.startingAmmo()),
                new BattleMarineDollPlate.Kit(copy.apply("gearCatalogSystemBreacherAssistDisplayName"),
                        copy.apply("battleMarineDollReady"), false, "shield", 1, 1, true))));
        return plate;
    }

    /** The hover-only help tab: collapsed ({@code carrier == null}) or opened for a carrier. */
    private static BufferedImage helpFrame(HeadlessUiRenderer renderer, Path modRoot,
                                           BattleDirectControlStatus.Carrier carrier) throws Exception {
        Reactor reactor = new Reactor();
        var plate = new BattleDirectControlHelpPlate(reactor, ModStrings.fromDisk(modRoot));
        var loader = new MarkupLoader(path -> Files.readString(modRoot.resolve(path)),
                List.of(BattleDirectControlHelpPlate.COMPONENT_PATH));
        try (var markup = loader.reloadAndBuild(reactor, BattleDirectControlHelpPlate.COMPONENT, plate.props())) {
            boolean open = carrier != null;
            plate.update(open, open ? carrier : BattleDirectControlStatus.Carrier.NONE);
            markup.flush();
            int width = (int) (open ? BattleDirectControlHelp.PANEL_WIDTH : BattleDirectControlHelp.TAB_WIDTH);
            int height = (int) (BattleDirectControlHelp.TAB_HEIGHT + (open
                    ? BattleDirectControlHelp.panelHeight(BattleDirectControlHelpPlate.rowCount(carrier)) : 0f));
            var document = BattleDirectControlOverlay.document(markup);
            document.layout(width, height);
            return renderer.render(document, width, height);
        }
    }

    /** Both panels side by side at 100%, bottom edges level, as a pilot reads them. */
    private static BufferedImage panels(HeadlessUiRenderer renderer, BattleDirectControlHudTest.Fixture fixture)
            throws Exception {
        int statusWidth = (int) BattleDirectControlOverlay.STATUS_WIDTH;
        int weaponsWidth = (int) BattleDirectControlOverlay.WEAPONS_WIDTH;
        float[] measured = measure(renderer, fixture);
        int statusHeight = Math.round(measured[0]);
        int weaponsHeight = Math.round(measured[1]);
        int height = Math.max(statusHeight, weaponsHeight);
        BufferedImage image = new BufferedImage(statusWidth + 24 + weaponsWidth, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 25, 30));
            graphics.fillRect(0, 0, image.getWidth(), height);
            graphics.drawImage(renderer.render(fixture.statusDocument(), statusWidth, statusHeight),
                    0, height - statusHeight, null);
            graphics.drawImage(renderer.render(fixture.weaponsDocument(), weaponsWidth, weaponsHeight),
                    statusWidth + 24, height - weaponsHeight, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * The panels' heights with the renderer's font metrics installed, as the game measures them.
     * A fixture's own measure runs before any font has metrics, so its text has no height.
     */
    static float[] measure(HeadlessUiRenderer renderer, BattleDirectControlHudTest.Fixture fixture) {
        renderer.render(fixture.statusDocument(), (int) BattleDirectControlOverlay.STATUS_WIDTH, 64);
        renderer.render(fixture.weaponsDocument(), (int) BattleDirectControlOverlay.WEAPONS_WIDTH, 64);
        float status = BattleDirectControlOverlay.naturalHeight(fixture.statusDocument(), fixture.status(),
                "direct-status-spacer", BattleDirectControlOverlay.STATUS_WIDTH);
        if (BattleDirectControlOverlay.hasDoll(fixture.model().carrier())) {
            status = Math.max(status, BattleDamageDollPlate.DOCUMENT_HEIGHT);
        }
        float weapons = BattleDirectControlOverlay.naturalHeight(fixture.weaponsDocument(), fixture.weapons(),
                "direct-weapons-spacer", BattleDirectControlOverlay.WEAPONS_WIDTH);
        return new float[]{status, weapons};
    }

    /** Active control: status bottom left beside the doll, weapon groups bottom right, exit/pause top right. */
    private static BufferedImage activeComposite(HeadlessUiRenderer renderer, Path modRoot,
                                                  int width, int height, float uiScale,
                                                  boolean paused) throws Exception {
        MechLoadoutComponent hound = damagedHound();
        return activeComposite(renderer, modRoot, width, height, uiScale, houndSnapshot(hound),
                sectionPlate(modRoot, hound).document(), paused);
    }

    /**
     * The direct-control status of the same damaged Hound the section plate draws: its SRM pod is
     * gone, so the nose chaingun is its only weapon card, and its durability is the sections' totals.
     */
    private static BattleDirectControlStatus.Snapshot houndSnapshot(MechLoadoutComponent hound) {
        MechSections sections = hound.sections();
        float maxStructure = 0f;
        float maxArmor = 0f;
        for (MechSection section : MechSection.values()) {
            maxStructure += sections.maxStructure(section);
            maxArmor += sections.maxArmor(section);
        }
        var durability = new BattleDirectControlStatus.Durability(sections.totalStructure(), maxStructure,
                sections.totalArmor(), maxArmor, MechVariant.HOUND.armorRating);
        List<BattleDirectControlStatus.WeaponStatus> weapons = new ArrayList<>();
        for (var mount : hound.mounts()) {
            if (mount == null) continue;
            String name = mount.component == MechWeaponComponent.NOSE_CHAINGUN ? "Nose chaingun" : mount.weaponId();
            weapons.add(new BattleDirectControlStatus.WeaponStatus(weapons.size() + 1, mount.slot.name(), name,
                    new BattleDirectControlStatus.Behavior(BattleDirectControlStatus.FireKind.BURST,
                            mount.component.projectilesPerTrigger, .1f, 1),
                    true, 0f, 1f, 0, .1f, new BattleDirectControlStatus.Ammo(-1, -1,
                            BattleDirectControlStatus.AmmoUnit.TRIGGER_PACKS, true)));
        }
        return new BattleDirectControlStatus.Snapshot(101, "Hound Lead", BattleDirectControlStatus.Carrier.MECH,
                durability, weapons);
    }

    private static BufferedImage activeComposite(HeadlessUiRenderer renderer, Path modRoot,
                                                  int width, int height, float uiScale,
                                                  BattleDirectControlStatus.Snapshot snapshot,
                                                  UiDocument doll,
                                                  boolean paused) throws Exception {
        return activeComposite(renderer, modRoot, width, height, uiScale, snapshot, doll, paused, false);
    }

    /** {@code wideStatus}: no doll, and the status panel as wide as the weapon groups instead. */
    private static BufferedImage activeComposite(HeadlessUiRenderer renderer, Path modRoot,
                                                  int width, int height, float uiScale,
                                                  BattleDirectControlStatus.Snapshot snapshot,
                                                  UiDocument doll,
                                                  boolean paused, boolean wideStatus) throws Exception {
        return activeComposite(renderer, modRoot, width, height, uiScale, snapshot, doll, paused, wideStatus, null);
    }

    /** {@code world}, when given, paints the battlefield over the backdrop and under the HUD. */
    static BufferedImage activeComposite(HeadlessUiRenderer renderer, Path modRoot,
                                                  int width, int height, float uiScale,
                                                  BattleDirectControlStatus.Snapshot snapshot,
                                                  UiDocument doll,
                                                  boolean paused, boolean wideStatus,
                                                  Consumer<Graphics2D> world) throws Exception {
        var layout = BattleDirectControlOverlayTest.layout(width, height, uiScale);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 25, 30));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(25, 34, 39));
            for (int x = 0; x < width; x += 48) graphics.drawLine(x, 0, x, height);
            for (int y = 0; y < height; y += 48) graphics.drawLine(0, y, width, y);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (world != null) world.accept(graphics);
            var objectiveReactor = new Reactor();
            var objectives = new BattleManualObjectiveModel(objectiveReactor);
            objectives.update(BattleManualObjectiveOverlayTest.preview());
            try (var markup = build(modRoot, objectiveReactor, BattleManualObjectiveOverlay.COMPONENT_PATH,
                    BattleManualObjectiveOverlay.COMPONENT, objectives.props())) {
                draw(graphics, renderer, markup, layout.manualObjectives(), layout.host(), uiScale);
            }
            try (var fixture = BattleDirectControlHudTest.fixture(modRoot,
                    snapshot, 0, paused, ignored -> {}, () -> {}, () -> {})) {
                float[] measured = measure(renderer, fixture);
                var status = BattleDirectControlOverlayTest.activeStatus(width, height, uiScale, measured[0], doll != null);
                if (wideStatus) {
                    float scale = status.documentScale();
                    float wide = BattleDirectControlOverlay.WEAPONS_WIDTH;
                    renderer.render(fixture.statusDocument(), (int) wide, 64);
                    float tall = BattleDirectControlOverlay.naturalHeight(fixture.statusDocument(), fixture.status(),
                            "direct-status-spacer", wide);
                    status = new UiViewport(status.screenX(), status.screenY(), wide * scale, tall * scale, scale);
                }
                var weapons = BattleDirectControlOverlayTest.activeWeapons(width, height, uiScale, measured[1]);
                draw(graphics, renderer, fixture.status(), status, layout.host(), uiScale);
                draw(graphics, renderer, fixture.weapons(), weapons, layout.host(), uiScale);
                draw(graphics, renderer, fixture.actions(), layout.activeActions(), layout.host(), uiScale);
            }
            if (doll != null) draw(graphics, renderer, doll, layout.activeSections(), layout.host(), uiScale);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage composite(HeadlessUiRenderer renderer, Path modRoot,
                                            int width, int height, float uiScale,
                                            boolean mech) throws Exception {
        var layout = BattleDirectControlOverlayTest.layout(width, height, uiScale, mech);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 25, 30));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(25, 34, 39));
            for (int x = 0; x < width; x += 48) graphics.drawLine(x, 0, x, height);
            for (int y = 0; y < height; y += 48) graphics.drawLine(0, y, width, y);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Reactor reactor = new Reactor();
            var hud = new BattleHudOverlayModel(reactor, ignored -> {}, "Pause", "1x", "2x", "4x");
            Map<String, Object> hudProps = hud.props();
            hudProps.put("commandPanelClasses", "conquest-command-panel");
            hudProps.put("commandPhase", "FRONT ADJUST");
            hudProps.put("commandForce", "8 SQUADS  |  79 MARINES  |  1 RESERVE");
            try (MarkupInstance markup = build(modRoot, reactor, BattleHudOverlay.COMPONENT_PATH,
                    BattleHudOverlay.COMPONENT, hudProps)) {
                BattleHudOverlay.wireLayout(markup);
                draw(graphics, renderer, markup, layout.hud(), layout.host(), uiScale);
            }
            if (mech) {
                var model = new BattleMechOverlayModel(reactor, () -> {}, (id, role) -> {},
                        id -> {});
                model.updateProjected(new BattleMechOverlayModel.MechState(3, 303L,
                        "Bulwark Lead", "Bulwark", MechRole.BALANCED, MechRole.ASSAULT,
                        null, BattleMechOverlayModel.selectableRoles()));
                try (MarkupInstance markup = build(modRoot, reactor, BattleMechOverlay.COMPONENT_PATH,
                        BattleMechOverlay.COMPONENT, model.props())) {
                    BattleMechOverlay.wireLayout(markup);
                    draw(graphics, renderer, markup, layout.mech(), layout.host(), uiScale);
                }
                var plate = sectionPlate(modRoot, new MechLoadoutComponent(MechVariant.BULWARK, null));
                draw(graphics, renderer, plate.document(), BattleDamageDollPlate.above(layout.mech()),
                        layout.host(), uiScale);
            } else {
                var model = new BattleSquadOverlayModel(reactor, () -> {});
                List<BattleSquadOverlayModel.MemberState> members = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    members.add(new BattleSquadOverlayModel.MemberState(i + 1L, i / 4, i == 0,
                            i == 0 ? "Rhea Voss" : "Marine " + (i + 1), 100f, 100f,
                            40f, 40f, 12f, "Assault rifle", "AR", new Color(140, 185, 210),
                            "", "", "", "", "", "Regular", "Steady", UnitRole.COMBATANT,
                            ArmorClass.STANDARD, 0, 0, 0, 0f, false, null));
                }
                model.updateProjected(new BattleSquadOverlayModel.SquadState("Squad 20", 12, 12,
                        .72f, members));
                try (MarkupInstance markup = build(modRoot, reactor, BattleSquadOverlay.COMPONENT_PATH,
                        BattleSquadOverlay.COMPONENT, model.props())) {
                    BattleSquadOverlay.wireLayout(markup);
                    draw(graphics, renderer, markup, layout.squad(), layout.host(), uiScale);
                }
            }
            var orders = new BattleOrderOverlayModel(reactor, mode -> {});
            orders.update(1, mech, Mode.NONE);
            try (MarkupInstance markup = build(modRoot, reactor, BattleOrderOverlay.COMPONENT_PATH,
                    BattleOrderOverlay.COMPONENT, orders.props())) {
                draw(graphics, renderer, markup, layout.orders(), layout.host(), uiScale);
            }
            try (MarkupInstance markup = BattleDirectControlOverlayTest.fixture(
                    modRoot, false, true, mech, () -> {})) {
                draw(graphics, renderer, markup, layout.control(), layout.host(), uiScale);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * The section paper-doll in four states, each plate at 100% on a 1920x1080 frame: an intact
     * Hound, a Hound whose SRM pod was shot off and whose torso armor is stripped, a crippled Hound
     * with its sensors gone, and an intact Bulwark. Damage goes through {@link MechSections#resolve},
     * the path combat uses; a destroyed mount is then struck off the loadout as combat does.
     */
    private static BufferedImage sectionStates(HeadlessUiRenderer renderer, Path modRoot) {
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 25, 30));
            graphics.fillRect(0, 0, 1920, 1080);
            graphics.setColor(new Color(25, 34, 39));
            for (int x = 0; x < 1920; x += 48) graphics.drawLine(x, 0, x, 1080);
            for (int y = 0; y < 1080; y += 48) graphics.drawLine(0, y, 1920, y);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(graphics.getFont().deriveFont(14f));
            List<MechLoadoutComponent> mechs = List.of(
                    new MechLoadoutComponent(MechVariant.HOUND, null), damagedHound(),
                    crippledHound(), new MechLoadoutComponent(MechVariant.BULWARK, null));
            List<String> captions = List.of("Hound, intact", "Hound, SRM pod destroyed, torso armor stripped",
                    "Hound, crippled, sensors lost", "Bulwark, intact");
            int width = (int) BattleDamageDollPlate.DOCUMENT_WIDTH;
            int height = (int) BattleDamageDollPlate.DOCUMENT_HEIGHT;
            int pitch = 420;
            int left = (1920 - pitch * 3 - width) / 2;
            for (int i = 0; i < mechs.size(); i++) {
                var plate = sectionPlate(modRoot, mechs.get(i));
                BufferedImage panel = renderer.render(plate.document(), width, height);
                int x = left + i * pitch;
                int y = (1080 - height) / 2;
                graphics.drawImage(panel, x, y, null);
                graphics.setColor(new Color(0xC5, 0xD6, 0xCB));
                graphics.drawString(captions.get(i), x, y - 14);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * A Marine for the doll: armor and health fractions and limb wounds, carrying (with {@code kit}) a
     * rocket launcher with one round of two left, a breaching assist, and an emptied medic kit.
     */
    private static BattleMarineDollPlate.MarineDoll marine(Path modRoot, boolean suit, int arms, int legs,
                                                          float armor, float health) {
        var copy = ModStrings.fromDisk(modRoot);
        List<BattleMarineDollPlate.Kit> items = List.of(
                new BattleMarineDollPlate.Kit(copy.apply("gearCatalogSpecialRocketLauncherDisplayName"), "1/3", false,
                        "missile", 1, 3),
                new BattleMarineDollPlate.Kit(copy.apply("gearCatalogSystemBreacherAssistDisplayName"),
                        copy.apply("battleMarineDollReady"), false, "shield", 1, 1, true),
                new BattleMarineDollPlate.Kit(copy.apply("battleMarineDollMedic"), "0/3", true));
        return new BattleMarineDollPlate.MarineDoll(suit, true, armor, health, arms, legs, items);
    }

    /** The preview Marine (74% health, armor 35 of 60, as its status panel reads) with a leg wound. */
    private static BattleMarineDollPlate marinePlate(Path modRoot, boolean showKit) {
        var plate = new BattleMarineDollPlate(path -> Files.readString(modRoot.resolve(path)),
                ModStrings.fromDisk(modRoot), showKit);
        plate.update(marine(modRoot, false, 0, 1, 35f / 60f, .74f));
        return plate;
    }

    /**
     * The Marine doll in five states on one 1920x1080 frame at 100%: whole, armor worn, armor gone and
     * hurt with two leg wounds, an arm and a leg wounded to the floor, and a power suit.
     */
    private static BufferedImage marineStates(HeadlessUiRenderer renderer, Path modRoot) {
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 25, 30));
            graphics.fillRect(0, 0, 1920, 1080);
            graphics.setColor(new Color(25, 34, 39));
            for (int x = 0; x < 1920; x += 48) graphics.drawLine(x, 0, x, 1080);
            for (int y = 0; y < 1080; y += 48) graphics.drawLine(0, y, 1920, y);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(graphics.getFont().deriveFont(14f));
            List<BattleMarineDollPlate.MarineDoll> dolls = List.of(
                    marine(modRoot, false, 0, 0, 1f, 1f),
                    marine(modRoot, false, 0, 0, .45f, 1f),
                    marine(modRoot, false, 0, 2, 0f, .55f),
                    marine(modRoot, false, 4, 4, 0f, .2f),
                    marine(modRoot, true, 0, 1, .8f, 1f));
            List<String> captions = List.of("Whole", "Armor worn", "Armor gone, hurt, legs x2",
                    "Arm and leg at the floor", "Power suit, leg wound");
            int width = (int) BattleMarineDollPlate.DOCUMENT_WIDTH;
            int height = (int) BattleMarineDollPlate.DOCUMENT_HEIGHT;
            int pitch = 340;
            int left = (1920 - pitch * 4 - width) / 2;
            for (int i = 0; i < dolls.size(); i++) {
                var plate = new BattleMarineDollPlate(path -> Files.readString(modRoot.resolve(path)),
                        ModStrings.fromDisk(modRoot), true);
                plate.update(dolls.get(i));
                BufferedImage panel = renderer.render(plate.document(), width, height);
                int x = left + i * pitch;
                int y = (1080 - height) / 2;
                graphics.drawImage(panel, x, y, null);
                graphics.setColor(new Color(0xC5, 0xD6, 0xCB));
                graphics.drawString(captions.get(i), x, y - 14);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BattleDamageDollPlate sectionPlate(Path modRoot, MechLoadoutComponent loadout) {
        var plate = new BattleDamageDollPlate(path -> Files.readString(modRoot.resolve(path)),
                ModStrings.fromDisk(modRoot));
        plate.update(loadout);
        return plate;
    }

    /** The preview Heavy APC's doll: whole plates over a hull at the status panel's 610 of 900. */
    private static BattleDamageDollPlate vehiclePlate(Path modRoot) {
        var plate = new BattleDamageDollPlate(path -> Files.readString(modRoot.resolve(path)),
                ModStrings.fromDisk(modRoot));
        plate.updateVehicle(VehicleType.HEAVY_APC, 610f, 900f, null, HullLocations.of(VehicleType.HEAVY_APC, null));
        return plate;
    }

    /** An unhurt breakthrough tank's doll. */
    private static BattleDamageDollPlate tankPlate(Path modRoot) {
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        HullTurretSection turret = HullTurretSection.forHull(type);
        HullLocations locations = HullLocations.of(type, turret);
        var plate = new BattleDamageDollPlate(path -> Files.readString(modRoot.resolve(path)),
                ModStrings.fromDisk(modRoot));
        float maxHp = type.maxStructure + locations.maxDrive();
        plate.updateVehicle(type, maxHp, maxHp, turret, locations);
        return plate;
    }

    private record FlankedTank(BattleDirectControlStatus.Snapshot status, BattleDamageDollPlate plate) { }

    /**
     * A breakthrough tank shot from its left through the production located-damage law: the left plate
     * stripped and the hull behind it hit, the drive gone (immobilised), and one round that found the
     * turret. Front, right and rear plates stay whole.
     */
    private static FlankedTank flankedTank(Path modRoot) {
        VehicleType type = VehicleType.BREAKTHROUGH_TANK;
        HullTurretSection turret = HullTurretSection.forHull(type);
        HullLocations locations = HullLocations.of(type, turret);
        float maxHp = type.maxStructure + locations.maxDrive();
        float maxArmor = locations.maxTotalArmor();
        float hp = maxHp;
        float armor = maxArmor;
        var scratch = new DurabilityModel.Resolution();
        var hit = new HullLocations.Hit();
        float rating = type.armorRating;
        float[][] shots = {{90f, 0f}, {90f, 0f}, {90f, 0f}, {90f, 0f}, {90f, 1f}, {90f, 0f}, {90f, 1f}, {90f, 0f}};
        for (float[] shot : shots) {
            locations.resolve(false, shot[1] > 0f, HullLocations.Plate.LEFT, shot[0], 40f, rating, 1f,
                    rating, .9f, hp, scratch, hit);
            hp -= hit.structureDamage();
            armor -= hit.armorDamage();
        }
        locations.resolve(true, false, HullLocations.Plate.LEFT, 70f, 40f, rating, .9f, rating, .9f, hp,
                scratch, hit);
        hp -= hit.structureDamage();
        armor -= hit.armorDamage();
        var plate = new BattleDamageDollPlate(path -> Files.readString(modRoot.resolve(path)),
                ModStrings.fromDisk(modRoot));
        plate.updateVehicle(type, hp, maxHp, turret, locations);
        var base = BattleDirectControlHudTest.preview(BattleDirectControlStatus.Carrier.VEHICLE, false);
        var status = new BattleDirectControlStatus.Snapshot(base.entityId(), "Breakthrough Tank",
                BattleDirectControlStatus.Carrier.VEHICLE, new BattleDirectControlStatus.Durability(hp,
                maxHp, armor, maxArmor, rating), base.weapons());
        return new FlankedTank(status, plate);
    }

    private static MechLoadoutComponent damagedHound() {
        var hound = new MechLoadoutComponent(MechVariant.HOUND, null);
        MechSections sections = sections(hound);
        hit(hound, MechSection.LEFT_SHOULDER, sections.armor(MechSection.LEFT_SHOULDER)
                + sections.structure(MechSection.LEFT_SHOULDER) + 1f);
        hit(hound, MechSection.TORSO, sections.armor(MechSection.TORSO));
        hit(hound, MechSection.LEGS, sections.armor(MechSection.LEGS) * 0.4f);
        return hound;
    }

    private static MechLoadoutComponent crippledHound() {
        var hound = new MechLoadoutComponent(MechVariant.HOUND, null);
        MechSections sections = sections(hound);
        for (MechSection section : List.of(MechSection.LEGS, MechSection.HEAD)) {
            hit(hound, section, sections.armor(section) + sections.structure(section) + 1f);
        }
        hit(hound, MechSection.ARMS, sections.armor(MechSection.ARMS) * 0.7f);
        return hound;
    }

    private static MechSections sections(MechLoadoutComponent mech) {
        MechVariant v = mech.variant;
        return mech.sectionsFor(v.maxStructure, v.maxStructure, v.armorCapacity, v.armorCapacity);
    }

    /** One full-penetration hit through the combat resolution; a mount at zero loses its weapon. */
    private static void hit(MechLoadoutComponent mech, MechSection section, float damage) {
        MechSections sections = sections(mech);
        sections.resolve(section, damage, mech.variant.armorRating, 0f, mech.variant.armorRating,
                new DurabilityModel.Resolution(), new MechSections.Hit());
        if (section.mount() != null && sections.destroyed(section)) mech.destroyMount(section.mount());
    }

    private static void draw(Graphics2D graphics, HeadlessUiRenderer renderer, UiDocument doll,
                             UiViewport viewport, UiViewport host, float uiScale) {
        BufferedImage panel = renderer.render(doll,
                Math.round(viewport.documentWidth()), Math.round(viewport.documentHeight()));
        int x = Math.round((viewport.screenX() - host.screenX()) * uiScale);
        int y = Math.round((host.screenY() + host.height() - viewport.screenY() - viewport.height()) * uiScale);
        graphics.drawImage(panel, x, y, Math.round(viewport.width() * uiScale),
                Math.round(viewport.height() * uiScale), null);
    }

    private static MarkupInstance build(Path modRoot, Reactor reactor, String path,
                                         String component, Map<String, Object> props) throws Exception {
        MarkupLoader loader = new MarkupLoader(file -> Files.readString(modRoot.resolve(file)), List.of(path));
        loader.reload();
        return loader.build(reactor, component, props);
    }

    private static void draw(Graphics2D graphics, HeadlessUiRenderer renderer, MarkupInstance markup,
                             UiViewport viewport, UiViewport host, float uiScale) {
        BufferedImage panel = renderer.render(BattleDirectControlOverlayTest.document(markup),
                Math.round(viewport.documentWidth()), Math.round(viewport.documentHeight()));
        int x = Math.round((viewport.screenX() - host.screenX()) * uiScale);
        int y = Math.round((host.screenY() + host.height() - viewport.screenY() - viewport.height()) * uiScale);
        graphics.drawImage(panel, x, y, Math.round(viewport.width() * uiScale),
                Math.round(viewport.height() * uiScale), null);
    }
}
