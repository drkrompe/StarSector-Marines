package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.assault.MachineAssaults;
import com.dillon.starsectormarines.battle.assault.MachineGuards;
import com.dillon.starsectormarines.battle.decision.goap.action.LinePlacement;
import com.dillon.starsectormarines.battle.mech.MechAttachmentService;
import com.dillon.starsectormarines.battle.mech.MechAttachmentSystem;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.HoldPlaceDecision;
import com.dillon.starsectormarines.battle.squad.OccupyPositionDecision;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderSystem;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fans a world gesture out through the existing simulation-owned order mailboxes.
 *
 * <p>A selection's <b>recipients</b> are what a group order moves: each infantry squad, each mech
 * chassis not following a squad that is also selected, and each vehicle. A mech attached to a selected
 * squad is not a recipient — it keeps its slot by that squad — while a mech selected without its
 * squad is one, and the direct order it receives detaches it in the simulation.
 */
public final class SelectionOrders {
    /**
     * The shortest right drag, in cells, read as a heading rather than as the click it nearly was.
     * Under it the angle is noise from the hand rather than intent.
     */
    public static final float MIN_FACING_DRAG_CELLS = 1f;
    /** Cells between neighbouring destinations when a group click spreads round the clicked cell. */
    public static final float GROUP_SPACING_CELLS = 4f;
    /** A mech's or a vehicle's share of a group line, in marines: a single unit with a broad footprint. */
    static final float MACHINE_LINE_WIDTH = 2f;
    /** How near the clicked cell's centre a friendly marine must stand for a mech's click to mean "attach". */
    static final float ATTACH_PICK_CELLS = 1.2f;

    /** What a recipient is, which decides the order it is handed. */
    public enum RecipientKind { INFANTRY, MECH, VEHICLE }

    /**
     * One thing a group order moves: an infantry squad ({@code id} unused), one mech chassis or one
     * vehicle ({@code id} its entity), standing at ({@code x}, {@code y}).
     */
    public record Recipient(RecipientKind kind, int squadId, long id, float x, float y) { }

    private SelectionOrders() { }

    /** Every recipient of the selection, squads in selection order and then vehicles. */
    public static List<Recipient> recipients(BattleSimulation sim, Selection selection) {
        List<Recipient> out = new ArrayList<>();
        if (sim == null || selection == null || selection.hasWarshipSelection()) return out;
        MechAttachmentService attachments = sim.getMechAttachmentService();
        List<Integer> squads = selection.getSelectedSquadIds();
        long primary = selection.getSelectedUnitEntityId();
        // A single clicked mech is the exact chassis, not its whole legacy lance.
        boolean exactMech = squads.size() == 1 && selection.vehicleCount() == 0 && primary != 0L
                && sim.world().isAlive(primary) && sim.world().hasMechLoadout(primary);
        for (int id : squads) {
            Squad squad = sim.getSquad(id);
            if (squad == null || squad.faction != Faction.MARINE) continue;
            if (squad.isMechSquad()) {
                if (squad.rescuePickupMech) continue;
                int anchor = attachments.anchorOf(id);
                if (anchor != MechAttachmentService.NONE && selection.isSquadSelected(anchor)) continue;
                for (int i = 0; i < sim.squadMemberCount(id); i++) {
                    long member = sim.squadMemberAt(id, i);
                    if (!sim.world().isAlive(member) || !sim.world().hasMechLoadout(member)) continue;
                    if (exactMech && member != primary) continue;
                    out.add(new Recipient(RecipientKind.MECH, id, member,
                            sim.world().x(member), sim.world().y(member)));
                }
            } else if (!squad.isDroneSquad()) {
                out.add(new Recipient(RecipientKind.INFANTRY, id, 0L, squad.centroidX, squad.centroidY));
            }
        }
        for (long vehicle : selection.getSelectedVehicleIds()) {
            if (sim.convoy().mission(vehicle) == null || sim.convoy().faction(vehicle) != Faction.MARINE) continue;
            GroundBody body = sim.convoy().body(vehicle);
            out.add(new Recipient(RecipientKind.VEHICLE, Selection.NONE, vehicle, body.x, body.y));
        }
        return out;
    }

    /**
     * The friendly infantry squad a right-click at the cell would attach the selection's mechs to, or
     * {@link Selection#NONE}: only a selection of player mechs alone attaches, and only on a player
     * infantry squad standing under the click.
     */
    public static int attachAnchorAt(BattleSimulation sim, Selection selection, int x, int y) {
        if (sim == null || selection == null || selection.hasWarshipSelection()
                || selection.vehicleCount() > 0) return Selection.NONE;
        boolean anyMech = false;
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad == null) continue;
            if (!squad.isMechSquad()) return Selection.NONE;
            if (squad.faction == Faction.MARINE && !squad.rescuePickupMech) anyMech = true;
        }
        if (!anyMech) return Selection.NONE;
        float cx = x + .5f;
        float cy = y + .5f;
        int best = Selection.NONE;
        float bestDistSq = ATTACH_PICK_CELLS * ATTACH_PICK_CELLS;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.squad().hasSquad(unit)) continue;
            int squadId = sim.squad().squadId(unit);
            if (MechAttachmentSystem.validAnchor(squadId, sim) == null) continue;
            float dx = sim.world().renderX(unit) - cx;
            float dy = sim.world().renderY(unit) - cy;
            float d2 = dx * dx + dy * dy;
            if (d2 <= bestDistSq) {
                bestDistSq = d2;
                best = squadId;
            }
        }
        return best;
    }

    public static void requestMove(BattleSimulation sim, Selection selection, int x, int y) {
        requestMove(sim, selection, x, y, Float.NaN);
    }

    /**
     * R: the selected Marine squads that are already under way run, or walk again if every one of
     * them already runs. A squad with no standing order, a lance and anything else selected are left
     * alone. Whether any squad was under way to toggle.
     */
    public static boolean toggleRun(BattleSimulation sim, Selection selection) {
        if (sim == null || selection == null || sim.directControl().active()) return false;
        SquadMoveOrderService orders = sim.getSquadMoveOrderService();
        List<Integer> underWay = new ArrayList<>();
        boolean allRunning = true;
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad == null || squad.faction != Faction.MARINE || squad.isMechSquad()
                    || orders.activeOrder(id) == null) continue;
            underWay.add(id);
            allRunning &= orders.isRunning(id);
        }
        for (int id : underWay) {
            if (orders.isRunning(id) == allRunning) orders.toggleRun(id);
        }
        return !underWay.isEmpty();
    }

    /**
     * Whether a right drag on this selection means "go to where the drag started and face the way it
     * went": a lone vehicle or a lone mech. Anything more draws a line, each recipient holding a stretch
     * of it, and a warship has no use for a heading.
     */
    public static boolean takesFacing(BattleSimulation sim, Selection selection) {
        if (sim == null || selection == null || selection.hasWarshipSelection()) return false;
        List<Recipient> recipients = recipients(sim, selection);
        return recipients.size() == 1 && recipients.get(0).kind() != RecipientKind.INFANTRY;
    }

    /** The heading of a drag by ({@code dx}, {@code dy}) cells, in the sprite convention every chassis uses. */
    public static float facingOf(float dx, float dy) {
        return LayeredAppearance.wrapDegrees(AirBody.facingToward(dx, dy));
    }

    /**
     * {@link #requestMove(BattleSimulation, Selection, int, int)} that finishes on
     * {@code facingDegrees}, or on whatever heading the unit arrives with when that is {@code NaN}.
     * Vehicles and mechs take the heading; ships and infantry, which have no order for one, take only
     * the destination.
     *
     * <p>A selection of mechs alone clicking on a friendly infantry squad attaches them to it. A lone
     * recipient goes to the clicked cell. A group spreads its destinations round the click
     * ({@link #spreadSlots}) so its members do not stack on one cell — except where the click means
     * something particular to a recipient (a ride, a compound, a place to man, a place to assault or
     * guard, its own unload), which that recipient takes at the clicked cell as it would alone.
     */
    public static void requestMove(BattleSimulation sim, Selection selection, int x, int y,
                                   float facingDegrees) {
        if (sim == null || selection == null || sim.directControl().active()
                || !sim.getGrid().inBounds(x, y)) return;
        if (selection.hasWarshipSelection()) {
            sim.shipCommands().requestMove(selection.getSelectedWarship(), x + .5f, y + .5f);
            return;
        }
        int anchor = attachAnchorAt(sim, selection, x, y);
        if (anchor != Selection.NONE) {
            for (int id : selection.getSelectedSquadIds()) {
                Squad squad = sim.getSquad(id);
                if (squad != null && squad.isMechSquad() && squad.faction == Faction.MARINE
                        && !squad.rescuePickupMech) {
                    sim.getMechAttachmentService().requestAttach(id, anchor);
                }
            }
            return;
        }
        List<Recipient> recipients = recipients(sim, selection);
        if (recipients.isEmpty()) return;
        int[][] slots = recipients.size() == 1 ? new int[][]{{x, y}}
                : spreadSlots(positions(recipients), x, y, sim.getGrid());
        for (int i = 0; i < recipients.size(); i++) {
            Recipient recipient = recipients.get(i);
            boolean exact = recipients.size() == 1 || contextual(sim, recipient, x, y);
            send(sim, recipient, exact ? x : slots[i][0], exact ? y : slots[i][1], facingDegrees);
        }
    }

    private static void send(BattleSimulation sim, Recipient recipient, int x, int y, float facingDegrees) {
        switch (recipient.kind()) {
            case INFANTRY -> sim.getSquadMoveOrderService().requestMove(recipient.squadId(), x, y);
            case MECH -> sim.getMechMoveOrderService().requestMove(recipient.id(), x, y, facingDegrees);
            case VEHICLE -> {
                VehicleMoveOrderService vehicles = sim.getVehicleMoveOrderService();
                if (vehicles != null) vehicles.requestMove(recipient.id(), x, y, facingDegrees);
            }
        }
    }

    /** Whether the clicked cell means something particular to this recipient, which it then takes exactly. */
    private static boolean contextual(BattleSimulation sim, Recipient recipient, int x, int y) {
        switch (recipient.kind()) {
            case INFANTRY -> {
                Squad squad = sim.getSquad(recipient.squadId());
                return squad != null && (sim.transport().mountableVehicleFor(x, y, squad.faction, 0) != 0L
                        || SquadMoveOrderSystem.insideUncapturedCompound(x, y, sim)
                        || OccupyPositionDecision.runUnderClick(sim.fightingPositions(),
                                DigInSystem.isEnabled(), x, y) >= 0
                        || HoldPlaceDecision.placeUnderClick(sim.getTacticalMap(), x, y) != null);
            }
            case MECH -> {
                return MachineAssaults.targetUnderClick(sim, Faction.MARINE, x, y) != null
                        || MachineGuards.targetUnderClick(sim, Faction.MARINE, x, y) != null;
            }
            case VEHICLE -> {
                return sim.transport().pointsAt(recipient.id(), x, y)
                        || MachineAssaults.vehicleArmed(sim, recipient.id())
                        && (MachineAssaults.targetUnderClick(sim, Faction.MARINE, x, y) != null
                        || MachineGuards.targetUnderClick(sim, Faction.MARINE, x, y) != null);
            }
        }
        return false;
    }

    private static float[][] positions(List<Recipient> recipients) {
        float[][] positions = new float[recipients.size()][];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = new float[]{recipients.get(i).x(), recipients.get(i).y()};
        }
        return positions;
    }

    /**
     * Destinations for a group click at ({@code x}, {@code y}): a compact hex-packed cluster
     * {@link #GROUP_SPACING_CELLS} apart round the clicked cell, the clicked cell first, each handed to
     * the recipient whose place in the group's current layout best matches the slot's place in the
     * cluster. So a group keeps its rough shape, closed up, and nobody crosses the group to get to its
     * slot. Deterministic: ties go to the lower index. A slot off the map is pulled onto it, and a slot
     * on unwalkable ground moves to walkable ground within two cells when there is some; the order
     * systems snap the rest.
     *
     * @param positions where each recipient stands now, in recipient order
     * @param grid      the map, or {@code null} to skip the bounds and ground checks
     */
    public static int[][] spreadSlots(float[][] positions, int x, int y, NavigationGrid grid) {
        int n = positions.length;
        int[][] out = new int[n][];
        if (n == 0) return out;
        if (n == 1) {
            out[0] = new int[]{x, y};
            return out;
        }
        float[][] cluster = cluster(n);
        float[][] layout = new float[n][2];
        float meanX = 0f;
        float meanY = 0f;
        for (float[] p : positions) {
            meanX += p[0] / n;
            meanY += p[1] / n;
        }
        for (int i = 0; i < n; i++) {
            layout[i][0] = positions[i][0] - meanX;
            layout[i][1] = positions[i][1] - meanY;
        }
        // Compare shapes, not sizes: both the layout and the cluster are scaled to a unit spread.
        normalise(layout);
        float[][] shape = new float[n][2];
        float clusterMeanX = 0f;
        float clusterMeanY = 0f;
        for (float[] c : cluster) {
            clusterMeanX += c[0] / n;
            clusterMeanY += c[1] / n;
        }
        for (int i = 0; i < n; i++) {
            shape[i][0] = cluster[i][0] - clusterMeanX;
            shape[i][1] = cluster[i][1] - clusterMeanY;
        }
        normalise(shape);
        boolean[] recipientTaken = new boolean[n];
        boolean[] slotTaken = new boolean[n];
        for (int round = 0; round < n; round++) {
            int bestRecipient = -1;
            int bestSlot = -1;
            float best = Float.MAX_VALUE;
            for (int r = 0; r < n; r++) {
                if (recipientTaken[r]) continue;
                for (int s = 0; s < n; s++) {
                    if (slotTaken[s]) continue;
                    float dx = layout[r][0] - shape[s][0];
                    float dy = layout[r][1] - shape[s][1];
                    float d = dx * dx + dy * dy;
                    if (d < best - 1e-6f) {
                        best = d;
                        bestRecipient = r;
                        bestSlot = s;
                    }
                }
            }
            recipientTaken[bestRecipient] = true;
            slotTaken[bestSlot] = true;
            out[bestRecipient] = ground(x + .5f + cluster[bestSlot][0], y + .5f + cluster[bestSlot][1], grid);
        }
        return out;
    }

    /** {@code n} hex-lattice offsets round the origin, nearest first, the origin itself first. */
    private static float[][] cluster(int n) {
        List<float[]> points = new ArrayList<>();
        int rings = 1;
        while (1 + 3 * rings * (rings + 1) < n) rings++;
        float s = GROUP_SPACING_CELLS;
        float h = (float) (Math.sqrt(3) / 2.0) * s;
        for (int row = -rings; row <= rings; row++) {
            for (int col = -rings; col <= rings; col++) {
                // Axial coordinates (col, row) inside a hexagon of radius `rings`.
                if (Math.abs(col + row) > rings) continue;
                points.add(new float[]{s * (col + row * .5f), h * row});
            }
        }
        points.sort((a, b) -> {
            float da = a[0] * a[0] + a[1] * a[1];
            float db = b[0] * b[0] + b[1] * b[1];
            if (Math.abs(da - db) > 1e-3f) return Float.compare(da, db);
            return Double.compare(Math.atan2(a[1], a[0]), Math.atan2(b[1], b[0]));
        });
        return points.subList(0, n).toArray(new float[0][]);
    }

    private static void normalise(float[][] points) {
        float sum = 0f;
        for (float[] p : points) sum += p[0] * p[0] + p[1] * p[1];
        float rms = (float) Math.sqrt(sum / points.length);
        if (rms < 1e-4f) return;
        for (float[] p : points) {
            p[0] /= rms;
            p[1] /= rms;
        }
    }

    private static int[] ground(float px, float py, NavigationGrid grid) {
        int cx = (int) Math.floor(px);
        int cy = (int) Math.floor(py);
        if (grid == null) return new int[]{cx, cy};
        cx = Math.max(0, Math.min(grid.getWidth() - 1, cx));
        cy = Math.max(0, Math.min(grid.getHeight() - 1, cy));
        if (grid.isWalkable(cx, cy)) return new int[]{cx, cy};
        for (int r = 1; r <= 2; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (grid.inBounds(cx + dx, cy + dy) && grid.isWalkable(cx + dx, cy + dy)) {
                        return new int[]{cx + dx, cy + dy};
                    }
                }
            }
        }
        return new int[]{cx, cy};
    }

    /**
     * A right drag from ({@code x0},{@code y0}) to ({@code x1},{@code y1}). Every recipient holds its
     * own consecutive stretch of the line in the order they stand along it ({@link #alongDrag}), so no
     * two cross on the way, each as long as its width
     * ({@link #lineWidth}), each facing as an infantry line does —
     * toward the threat its squad believes in, else away from where it stands. An infantry squad
     * holds its stretch as a frontage; a mech or a vehicle goes to its stretch's middle and finishes
     * facing that front. A drag too short to be a line is the click it nearly was.
     */
    public static void requestLine(BattleSimulation sim, Selection selection, int x0, int y0, int x1, int y1) {
        if (sim == null || selection == null || sim.directControl().active()
                || !sim.getGrid().inBounds(x0, y0) || !sim.getGrid().inBounds(x1, y1)) return;
        List<Recipient> recipients = recipients(sim, selection);
        if (recipients.isEmpty() || Math.hypot(x1 - x0, y1 - y0) < FrontageLine.MIN_LENGTH) {
            requestMove(sim, selection, x1, y1);
            return;
        }
        recipients = alongDrag(recipients, x0, y0, x1, y1);
        List<FrontageLine> lines = previewLines(sim, selection, x0, y0, x1, y1);
        for (int i = 0; i < recipients.size(); i++) {
            Recipient recipient = recipients.get(i);
            FrontageLine line = lines.get(i);
            if (recipient.kind() == RecipientKind.INFANTRY) {
                List<Follower> followers = followersAlong(sim, recipient.squadId(), line);
                sim.getSquadMoveOrderService().requestLine(recipient.squadId(),
                        line.x0(), line.y0(), line.x1(), line.y1(), followers.size());
                FrontageLine within = line.withMachines(followers.size());
                for (int k = 0; k < followers.size(); k++) {
                    int[] post = followerPost(within, k);
                    sim.getMechAttachmentService().requestPost(followers.get(k).mechSquadId(), post[0], post[1],
                            facingOf(line.frontX(), line.frontY()), within);
                }
            } else {
                int[] spot = machineSpot(line);
                send(sim, recipient, spot[0], spot[1], facingOf(line.frontX(), line.frontY()));
            }
        }
    }

    /**
     * The stretch of a drag each recipient would hold, one per recipient in the order they stand
     * along the drag ({@link #alongDrag}), each
     * facing the way that recipient's order will face. Empty when nothing selected takes a line. The
     * preview and the order both come from here, so the arrow drawn under the drag is the facing
     * given.
     */
    public static List<FrontageLine> previewLines(BattleSimulation sim, Selection selection,
                                                  int x0, int y0, int x1, int y1) {
        List<Recipient> recipients = alongDrag(recipients(sim, selection), x0, y0, x1, y1);
        List<FrontageLine> lines = new ArrayList<>(recipients.size());
        float[] widths = new float[recipients.size()];
        float total = 0f;
        for (int i = 0; i < widths.length; i++) {
            widths[i] = lineWidth(sim, recipients.get(i));
            total += widths[i];
        }
        float before = 0f;
        for (int i = 0; i < widths.length; i++) {
            Recipient recipient = recipients.get(i);
            float from = before / total;
            before += widths[i];
            float to = before / total;
            int ax = Math.round(x0 + (x1 - x0) * from);
            int ay = Math.round(y0 + (y1 - y0) * from);
            int bx = Math.round(x0 + (x1 - x0) * to);
            int by = Math.round(y0 + (y1 - y0) * to);
            Squad squad = recipient.kind() == RecipientKind.VEHICLE ? null : sim.getSquad(recipient.squadId());
            SquadContactPicture picture = squad == null ? null : squad.contactPicture;
            boolean threat = picture != null && picture.hasContacts()
                    && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0;
            int threatX = threat ? picture.primaryCellX() : -1;
            int threatY = threat ? picture.primaryCellY() : -1;
            FrontageLine stretch = FrontageLine.facing(ax, ay, bx, by, recipient.x(), recipient.y(), threatX, threatY);
            if (stretch.length() < 1f) {
                // A single machine's stretch can round to a point; it faces as the whole drag does.
                FrontageLine whole = FrontageLine.facing(x0, y0, x1, y1, recipient.x(), recipient.y(), threatX, threatY);
                stretch = new FrontageLine(ax, ay, bx, by, whole.frontX(), whole.frontY());
            }
            lines.add(stretch);
        }
        return lines;
    }

    /**
     * How much of a group line a recipient takes, in marines: a squad one per live marine, a mech or
     * a vehicle {@link #MACHINE_LINE_WIDTH} — it is one unit, however wide its chassis.
     */
    static float lineWidth(BattleSimulation sim, Recipient recipient) {
        if (recipient.kind() != RecipientKind.INFANTRY) return MACHINE_LINE_WIDTH;
        return marines(sim, recipient) + MACHINE_LINE_WIDTH * followers(sim, recipient.squadId()).size();
    }

    private static float marines(BattleSimulation sim, Recipient recipient) {
        Squad squad = sim.getSquad(recipient.squadId());
        return squad == null ? 1f : Math.max(1, squad.aliveMembers);
    }

    /** A live mech attached to an infantry recipient, which takes a post on that squad's stretch of a line. */
    record Follower(int mechSquadId, long mech) { }

    /** The live mechs following {@code squadId}, in follower order. */
    static List<Follower> followers(BattleSimulation sim, int squadId) {
        List<Follower> out = new ArrayList<>();
        for (int mechSquad : sim.getMechAttachmentService().followersOf(squadId)) {
            for (int i = 0, n = sim.squadMemberCount(mechSquad); i < n; i++) {
                long mech = sim.squadMemberAt(mechSquad, i);
                if (sim.world().isAlive(mech) && sim.world().hasMechLoadout(mech)) out.add(new Follower(mechSquad, mech));
            }
        }
        return out;
    }

    /**
     * {@code recipients} in the order they stand along the drag from ({@code x0},{@code y0}) to
     * ({@code x1},{@code y1}), selection order among equals. Stretches are dealt out along the drag
     * in this order, so the recipient nearest its start takes the first and no two paths cross.
     */
    static List<Recipient> alongDrag(List<Recipient> recipients, int x0, int y0, int x1, int y1) {
        FrontageLine drag = new FrontageLine(x0, y0, x1, y1, 0f, 0f);
        List<Recipient> sorted = new ArrayList<>(recipients);
        sorted.sort(Comparator.comparingDouble(r -> drag.projection(r.x(), r.y())));
        return sorted;
    }

    /**
     * The live mechs following {@code squadId} in the order they stand along its {@code stretch}, so
     * the first band goes to the mech nearest the stretch's start and the two never cross.
     */
    static List<Follower> followersAlong(BattleSimulation sim, int squadId, FrontageLine stretch) {
        List<Follower> sorted = followers(sim, squadId);
        sorted.sort(Comparator.comparingDouble(
                f -> stretch.projection(sim.world().x(f.mech()), sim.world().y(f.mech()))));
        return sorted;
    }

    /**
     * Where the {@code k}th mech attached to an infantry recipient stands on its squad's stretch: the
     * middle of its band among the marines ({@link FrontageLine#machineFraction}), as far back as they.
     */
    static int[] followerPost(FrontageLine within, int k) {
        return round(within.pointAt(within.machineFraction(k), -LinePlacement.REARWARD_OFFSET));
    }

    /** Where a mech or a vehicle stands on its own stretch: the middle, as far back from the line as a marine. */
    static int[] machineSpot(FrontageLine stretch) {
        return round(stretch.pointAt(.5f, -LinePlacement.REARWARD_OFFSET));
    }

    /** A point on a line as the cell it names, rounded as a marine's share is ({@code LinePlacement}). */
    private static int[] round(float[] point) {
        return new int[]{Math.round(point[0]), Math.round(point[1])};
    }

    /**
     * {@link #previewLines} with where everything will stand on its stretch: every marine of each
     * squad by the claims' own dry run and the order's own overflow rule, and each mech or vehicle at
     * its stretch's middle, so a ring drawn under the drag is where each goes once it is released.
     */
    public static List<HighlightOverlay.LineMark> previewMarks(BattleSimulation sim, Selection selection,
                                                                int x0, int y0, int x1, int y1) {
        List<Recipient> recipients = alongDrag(recipients(sim, selection), x0, y0, x1, y1);
        List<FrontageLine> lines = previewLines(sim, selection, x0, y0, x1, y1);
        List<HighlightOverlay.LineMark> marks = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Recipient recipient = recipients.get(i);
            List<HighlightOverlay.PlaceMark> places = new ArrayList<>();
            if (recipient.kind() == RecipientKind.INFANTRY) {
                List<Follower> followers = followersAlong(sim, recipient.squadId(), lines.get(i));
                FrontageLine within = lines.get(i).withMachines(followers.size());
                for (LinePlacement.Place place : LinePlacement.preview(sim, sim.getSquad(recipient.squadId()), within)) {
                    places.add(new HighlightOverlay.PlaceMark(place.member(), place.x(), place.y(), place.position()));
                }
                for (int k = 0; k < followers.size(); k++) {
                    int[] post = followerPost(within, k);
                    places.add(new HighlightOverlay.PlaceMark(followers.get(k).mech(), post[0], post[1], false));
                }
            } else {
                int[] spot = machineSpot(lines.get(i));
                places.add(new HighlightOverlay.PlaceMark(recipient.id(), spot[0], spot[1], false));
            }
            marks.add(new HighlightOverlay.LineMark(lines.get(i), places));
        }
        return marks;
    }
}
