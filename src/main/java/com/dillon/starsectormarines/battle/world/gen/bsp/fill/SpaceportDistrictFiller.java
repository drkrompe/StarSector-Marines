package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.CompoundFiller;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Paints one civilian spaceport campus: authored landing berths occupy the
 * largest parcels while the remainder become a terminal and machinery yard.
 * Non-reserved margins of the local roads are reclaimed as shared apron so
 * the adjacent BSP leaves read as one facility without breaking the vehicle
 * graph's protected centerline.
 */
public final class SpaceportDistrictFiller implements CompoundFiller {

    private static final int BRIDGE_SCAN_DEPTH = 5;
    private final SpaceportFiller apron = new SpaceportFiller();
    private final BuildingCommercialFiller terminal = new BuildingCommercialFiller();
    private final BuildingIndustrialFiller warehouse = new BuildingIndustrialFiller();
    private final IndustrialYardFiller serviceYard = new IndustrialYardFiller();

    @Override
    public BlockKind kind() { return BlockKind.SPACEPORT_PAD; }

    @Override
    public void fill(Compound compound, GenContext ctx) {
        requireRoadOverlays(ctx);
        reclaimLocalRoadMargins(compound, ctx);

        List<BlockLeaf> parcels = new ArrayList<>(compound.members);
        parcels.sort(Comparator.comparingInt(BlockLeaf::area).reversed()
                .thenComparingInt(leaf -> leaf.top)
                .thenComparingInt(leaf -> leaf.left));
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        int requestedPads = profile != null && profile.spaceportTier() >= 2 ? 6 : 4;
        int padCount = Math.min(requestedPads, parcels.size());
        for (int i = 0; i < padCount; i++) apron.fill(parcels.get(i), ctx);

        int supportIndex = 0;
        for (int i = padCount; i < parcels.size(); i++, supportIndex++) {
            BlockLeaf parcel = parcels.get(i);
            if (supportIndex == 0) {
                terminal.fill(parcel, ctx);
            } else if (supportIndex % 2 == 1) {
                serviceYard.fill(parcel, ctx);
            } else {
                warehouse.fill(parcel, ctx);
            }
        }
    }

    private static void reclaimLocalRoadMargins(Compound compound, GenContext ctx) {
        boolean[][] roadCells = ctx.get(BspKeys.ROAD_CELLS);
        boolean[][] reserved = ctx.get(BspKeys.ROAD_RESERVATION);
        boolean[][] memberCells = new boolean[ctx.width][ctx.height];
        for (BlockLeaf member : compound.members) {
            for (int y = member.top; y <= member.bottom; y++) {
                for (int x = member.left; x <= member.right; x++) memberCells[x][y] = true;
            }
        }

        for (int y = compound.top; y <= compound.bottom; y++) {
            for (int x = compound.left; x <= compound.right; x++) {
                if (!roadCells[x][y] || reserved[x][y]) continue;
                boolean bridgedVertically = memberWithin(memberCells, x, y, 0, -1)
                        && memberWithin(memberCells, x, y, 0, 1);
                boolean bridgedHorizontally = memberWithin(memberCells, x, y, -1, 0)
                        && memberWithin(memberCells, x, y, 1, 0);
                if (!bridgedVertically && !bridgedHorizontally) continue;
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }
    }

    private static boolean memberWithin(boolean[][] memberCells, int x, int y, int dx, int dy) {
        for (int step = 1; step <= BRIDGE_SCAN_DEPTH; step++) {
            int nx = x + dx * step;
            int ny = y + dy * step;
            if (nx < 0 || nx >= memberCells.length || ny < 0 || ny >= memberCells[0].length) {
                return false;
            }
            if (memberCells[nx][ny]) return true;
        }
        return false;
    }
}
