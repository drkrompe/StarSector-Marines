package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.marine.MechCompartment;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.MachineDollExport;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every mech chassis's and vehicle hull's equipment doll as the Mech Lab draws it, each machine alone on the first
 * pad in its stock fit at 1920x1080 / 100%: {@code <id>.png} cropped to the doll's cards, {@code <id>-full.png} the
 * whole frame, and {@code manifest.json} listing each doll's sockets from the code paths that lay them out
 * ({@link MechFittingLayout#forVariant}, {@code VehicleDoll}). Opt-in: it is a review of the dolls, not evidence
 * any other change needs.
 */
public final class MachineDollSnapshotSuite implements SnapshotSuite {

    /** Margin kept around the cards in the cropped picture, in pixels. */
    private static final int CROP_MARGIN = 28;

    @Override public String id() { return "machine-dolls"; }
    @Override public String label() { return "Mech Lab equipment dolls, every machine"; }
    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        JSONArray manifest = new JSONArray();
        for (MechVariant variant : MechVariant.values()) {
            String id = variant.name().toLowerCase(Locale.ROOT);
            List<SocketDef> sockets = MechFittingLayout.forVariant(variant).sockets();
            JSONObject entry = new JSONObject()
                    .put("id", id).put("kind", "mech").put("name", variant.displayName())
                    .put("weightClass", String.valueOf(MechPattern.of(variant).weightClass()));
            render(context, renderer, "machine-mech." + variant.name(), id, sockets, artifacts,
                    (viewModel, socket) -> mechSocket(viewModel, socket), entry);
            manifest.put(entry);
        }
        for (VehicleType type : VehicleType.values()) {
            String id = type.name().toLowerCase(Locale.ROOT);
            List<SocketDef> sockets = MachineDollExport.vehicleSockets(type);
            MechPattern.WeightClass weightClass = MechPattern.weightClass(type);
            // Only a hull with a licence (a pattern tier) can stand in the lab: it is the hulls the store sells.
            if (weightClass == null) continue;
            JSONObject entry = new JSONObject()
                    .put("id", id).put("kind", "vehicle")
                    .put("name", CampaignVehicle.typeName(type))
                    .put("className", CampaignVehicle.className(type))
                    .put("weightClass", String.valueOf(weightClass))
                    .put("weight", type.weight().name());
            render(context, renderer, "machine-vehicle." + type.name(), id, sockets, artifacts,
                    (viewModel, socket) -> vehicleSocket(viewModel, type, socket), entry);
            manifest.put(entry);
        }
        Path out = context.projectRoot().resolve("build/snapshots").resolve(id()).resolve("manifest.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, manifest.toString(2));
        return artifacts;
    }

    private interface SocketReader {
        JSONObject read(MechLabViewModel viewModel, SocketDef socket) throws Exception;
    }

    private static void render(SnapshotContext context, HeadlessUiRenderer renderer, String hint, String id,
                               List<SocketDef> sockets, List<SnapshotArtifact> artifacts, SocketReader reader,
                               JSONObject entry) throws Exception {
        int[] crop = new int[4];
        JSONArray socketRows = new JSONArray();
        Exception[] failure = new Exception[1];
        BufferedImage full = UiSnapshotSuite.renderMachineDoll(context, renderer, hint, (viewModel, doll) -> {
            cropOf(sockets, doll, crop);
            try {
                for (SocketDef socket : sockets) socketRows.put(reader.read(viewModel, socket));
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) throw failure[0];
        entry.put("sockets", socketRows);
        int left = Math.max(0, crop[0] - CROP_MARGIN), top = Math.max(0, crop[1] - CROP_MARGIN);
        int right = Math.min(full.getWidth(), crop[2] + CROP_MARGIN);
        int bottom = Math.min(full.getHeight(), crop[3] + CROP_MARGIN);
        BufferedImage cropped = new BufferedImage(right - left, bottom - top, BufferedImage.TYPE_INT_ARGB);
        cropped.createGraphics().drawImage(full.getSubimage(left, top, right - left, bottom - top), 0, 0, null);
        entry.put("crop", new JSONArray(List.of(left, top, right - left, bottom - top)));
        artifacts.add(new SnapshotArtifact(id + ".png", cropped));
        artifacts.add(new SnapshotArtifact(id + "-full.png", full));
    }

    /** The cards' union on the doll canvas, as frame pixels (the lab's document is the frame at 1920x1080 / 100%). */
    private static void cropOf(List<SocketDef> sockets, CanvasMetrics doll, int[] crop) {
        float[] box = MachineDollExport.cardBounds(sockets, doll.surfaceWidth(), doll.surfaceHeight());
        crop[0] = (int) Math.floor(doll.toDocumentX(box[0]));
        crop[1] = (int) Math.floor(doll.toDocumentY(box[1]));
        crop[2] = (int) Math.ceil(doll.toDocumentX(box[2]));
        crop[3] = (int) Math.ceil(doll.toDocumentY(box[3]));
    }

    private static JSONObject socketRow(SocketDef socket) throws Exception {
        return new JSONObject().put("id", socket.id().name()).put("type", socket.type().name())
                .put("columns", socket.gridColumns()).put("rows", socket.gridRows())
                .put("label", socket.label()).put("factoryLocked", socket.factoryLocked());
    }

    private static JSONObject mechSocket(MechLabViewModel viewModel, SocketDef socket) throws Exception {
        JSONObject row = socketRow(socket);
        JSONArray holds = new JSONArray();
        if (socket.id().compartment()) {
            for (MechCompartment.Placement placed : viewModel.compartmentPlacements(socket.id())) {
                holds.put(part(placed.part().id, placed.part().displayName(), placed.columns(), placed.rows()));
            }
        } else if (MachineDollExport.mountFor(socket.id()) != null) {
            MechWeaponComponent weapon = viewModel.installedWeapon(socket.id());
            if (weapon != null) {
                holds.put(part(weapon.id, weapon.displayName(), weapon.footprintColumns, weapon.footprintRows));
            }
        } else {
            MechInternal internal = viewModel.installedInternal(socket.id());
            if (internal != null) {
                holds.put(part(internal.id, internal.displayName(), internal.footprintColumns,
                        internal.footprintRows));
            }
        }
        row.put("caption", holds.length() == 0 ? "" : holds.getJSONObject(0).getString("name"));
        return row.put("holds", holds);
    }

    private static JSONObject vehicleSocket(MechLabViewModel viewModel, VehicleType type, SocketDef socket) throws Exception {
        List<VehicleKit> kits = viewModel.installedKits();
        JSONObject row = socketRow(socket);
        row.put("caption", MachineDollExport.vehicleCaption(type, socket.id(), kits,
                viewModel.installedInternal(SocketId.PLATING), viewModel.installedInternal(SocketId.CORE),
                viewModel.installedWeapon(SocketId.ARMS)));
        if (socket.id() == SocketId.AUXILIARY) {
            row.put("kitSlots", VehicleKit.slots(type));
            JSONArray holds = new JSONArray();
            for (VehicleKit kit : kits) holds.put(part(kit.id, kit.displayName(), 1, 1));
            row.put("holds", holds);
        }
        return row;
    }

    private static JSONObject part(String id, String name, int columns, int rows) throws Exception {
        return new JSONObject().put("id", id).put("name", name).put("columns", columns).put("rows", rows);
    }
}
