package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechAttachmentService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.warship.Warship;

/**
 * Ground rings follow rendered bodies rather than their navigation cells. A mech attached to a
 * selected squad is ringed as selected, since orders to the squad carry it along; a faint tether joins
 * each attached mech to its squad while either is selected or hovered; and a vehicle in a group
 * selection is ringed with the rest of the group.
 */
public final class SelectionRingRenderSystem implements RenderSystem {
    @Override public RenderLayer layer() { return RenderLayer.HIGHLIGHTS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        Selection selection = ctx.selection;
        if (selection == null || ctx.sim == null || ctx.camera == null
                || ctx.sim.directControl().active()) return;
        var sim = ctx.sim;
        var camera = ctx.camera;
        ViewCull view = ViewCull.of(camera);
        Warship ship = selection.getSelectedWarship();
        Tender tender = ship == null ? null : sim.tenders().of(ship);
        if (ship != null && ship.faction == Faction.MARINE && !ship.destroyed() && !ship.departing() && !ship.departed()
                && (sim.warships().ships().contains(ship) || tender != null && tender.grounded() && tender.anchorAlive())) {
            float radius = Math.max(1f, ship.lengthCells * 0.5f + 0.4f);
            float centerX = ship.x, centerY = ship.y;
            if (tender != null && (tender.grounded() || tender.lowHover()) && tender.groundDisplay() != null) {
                Tender.GroundDisplay display = tender.groundDisplay();
                centerX = display.centerX();
                centerY = display.centerY();
                radius = display.enclosingRadius() + 0.4f;
            }
            if (view.visible(centerX, centerY, radius * 2f)) {
                ring(out, camera.cellToScreenX(centerX), camera.cellToScreenY(centerY),
                        radius * camera.cellPxSize(), true, true, ctx.alphaMult * 0.9f);
            }
            if (ship.remoteOrder() == Warship.RemoteOrder.MOVE || ship.remoteOrder() == Warship.RemoteOrder.HOLD) {
                float tx = camera.cellToScreenX(ship.stationX()), ty = camera.cellToScreenY(ship.stationY());
                out.addLine(RenderLayer.HIGHLIGHTS, camera.cellToScreenX(ship.x), camera.cellToScreenY(ship.y),
                        tx, ty, 1.2f, 1f, 0.83f, 0.39f, ctx.alphaMult * 0.65f);
                ring(out, tx, ty, camera.cellPxSize(), true, true, ctx.alphaMult * 0.75f);
            }
        }
        MechAttachmentService attachments = sim.getMechAttachmentService();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.squad().hasSquad(unit)) continue;
            int squad = sim.squad().squadId(unit);
            int anchor = attachments.anchorOf(squad);
            boolean selected = selection.isSquadSelected(squad)
                    || anchor != MechAttachmentService.NONE && selection.isSquadSelected(anchor);
            boolean hovered = squad == selection.getHoveredSquadId()
                    || anchor != MechAttachmentService.NONE && anchor == selection.getHoveredSquadId();
            if (!selected && !hovered) continue;
            int slot = sim.getRoster().indexOf(unit);
            byte visibility = sim.getFogOfWar().getUnitVisibility(slot);
            if (visibility == FogOfWarService.VIS_HIDDEN) continue;
            float x = sim.world().renderX(unit), y = sim.world().renderY(unit);
            float radius = Math.max(0.46f, sim.getRoster().radius(unit) + 0.12f);
            // A mech's chassis and arms draw well past its body radius; ring the silhouette, or the
            // chassis covers its own ring.
            if (sim.world().mechLoadout(unit) != null) {
                radius = Math.max(radius, MECH_RING_HULLS
                        * UnitRenderService.layeredMechHullWidth(1f, sim.getRoster().renderScale(unit)) + 0.15f);
            }
            if (!view.visible(x, y, radius * 2f)) continue;
            float alpha = ctx.alphaMult * (selected ? 0.9f : 0.55f);
            if (visibility == FogOfWarService.VIS_FADING) alpha *= sim.getFogOfWar().getFadeAlpha(slot);
            boolean primary = selected && unit == selection.getSelectedUnitEntityId();
            ring(out, camera.cellToScreenX(x), camera.cellToScreenY(y),
                    radius * camera.cellPxSize(), primary, selected, alpha);
        }
        if (selection.vehicleCount() + selection.squadCount() > 1) {
            for (long vehicle : selection.getSelectedVehicleIds()) {
                if (sim.convoy().mission(vehicle) == null) continue;
                GroundBody body = sim.convoy().body(vehicle);
                if (!view.visible(body.x, body.y, VEHICLE_RING_CELLS * 2f)) continue;
                ring(out, camera.cellToScreenX(body.x), camera.cellToScreenY(body.y),
                        VEHICLE_RING_CELLS * camera.cellPxSize(),
                        vehicle == selection.getSelectedVehicleId(), true, ctx.alphaMult * 0.9f);
            }
        }
        // A selected civilian rings the whole group a right click would rally, in the neutral yellow, itself bolder.
        if (selection.hasCivilianSelection()) {
            long picked = selection.getSelectedCivilianId();
            for (long civilian : sim.civilianRallyGroup(picked)) {
                float x = sim.world().renderX(civilian), y = sim.world().renderY(civilian);
                float radius = Math.max(0.46f, sim.getRoster().radius(civilian) + 0.12f);
                if (!view.visible(x, y, radius * 2f)) continue;
                boolean primary = civilian == picked;
                ring(out, camera.cellToScreenX(x), camera.cellToScreenY(y), radius * camera.cellPxSize(),
                        primary ? 2.2f : 1.5f, NEUTRAL_R, NEUTRAL_G, NEUTRAL_B, ctx.alphaMult * (primary ? 0.95f : 0.7f));
            }
        }
        if (attachments.hasAttachments()) tethers(ctx, attachments, view, out);
    }

    /** Ring radius of a mech in hull widths: half the roughly 1.6-hull silhouette its arms overhang to. */
    private static final float MECH_RING_HULLS = 0.8f;

    /** Ring radius of a group-selected vehicle, in cells; its click pick radius. */
    private static final float VEHICLE_RING_CELLS = 1.5f;

    /** A faint line from each attached mech to its squad's centre, while either end is selected or hovered. */
    private static void tethers(RenderContext ctx, MechAttachmentService attachments, ViewCull view, DrawList out) {
        Selection selection = ctx.selection;
        var sim = ctx.sim;
        var camera = ctx.camera;
        int hovered = selection.getHoveredSquadId();
        for (var entry : attachments.attachments().entrySet()) {
            int mechSquad = entry.getKey();
            int anchorId = entry.getValue();
            if (!selection.isSquadSelected(mechSquad) && !selection.isSquadSelected(anchorId)
                    && hovered != mechSquad && hovered != anchorId) continue;
            Squad anchor = sim.getSquad(anchorId);
            if (anchor == null || anchor.aliveMembers <= 0) continue;
            float ax = camera.cellToScreenX(anchor.centroidX);
            float ay = camera.cellToScreenY(anchor.centroidY);
            for (int i = 0, n = sim.squadMemberCount(mechSquad); i < n; i++) {
                long mech = sim.squadMemberAt(mechSquad, i);
                if (!sim.world().isAlive(mech)) continue;
                float mx = sim.world().renderX(mech);
                float my = sim.world().renderY(mech);
                if (!view.visible(mx, my, 2f) && !view.visible(anchor.centroidX, anchor.centroidY, 2f)) continue;
                out.addLine(RenderLayer.HIGHLIGHTS, camera.cellToScreenX(mx), camera.cellToScreenY(my), ax, ay,
                        1f, 0.5f, 1f, 0.5f, ctx.alphaMult * 0.35f);
            }
        }
    }

    /** A neutral selection, the rescue's civilians: yellow, apart from the company's green and amber. */
    static final float NEUTRAL_R = 1f, NEUTRAL_G = 0.92f, NEUTRAL_B = 0.2f;

    static void ring(DrawList out, float x, float y, float radius,
                     boolean primary, boolean selected, float alpha) {
        float r = primary ? 1f : selected ? 0.5f : 0.7f;
        float g = primary ? 0.83f : 1f;
        float b = primary ? 0.39f : selected ? 0.5f : 1f;
        ring(out, x, y, radius, selected ? 1.8f : 1.2f, r, g, b, alpha);
    }

    static void ring(DrawList out, float x, float y, float radius, float width,
                     float r, float g, float b, float alpha) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            double next = (i + 1) * Math.PI * 2 / 32;
            out.addLine(RenderLayer.HIGHLIGHTS,
                    x + radius * (float) Math.cos(a), y + radius * (float) Math.sin(a),
                    x + radius * (float) Math.cos(next), y + radius * (float) Math.sin(next),
                    width, r, g, b, alpha);
        }
    }
}
