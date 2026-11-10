package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;
import java.util.Set;

/**
 * A mine's site ({@code voids.md}, the open pit; owner: the mine stands): the head works as an ore-processing chain
 * beside its open pit, in sparse ore yards. The pit's mass driver lands the ore at the catch station on the rim, and
 * the raised ore line carries it to the receiving house and on through the chain; the landscape pass cuts the pit and
 * lays the line ({@code OutdoorLandscapeStage}), since the pit must be cut before the rest of the open country.
 */
public final class MiningWorks implements RaidIndustry {

    /** A mine's receiving house, where the ore line from the pit lands its ore on the primary crusher. */
    private static final RoomShape RECEIVING_HOUSE = RoomShape.rectangle(10, 8);
    /** The covered stockpile the crushed ore is stacked in before the mills draw it. */
    private static final RoomShape STOCKPILE_SHED = RoomShape.rectangle(18, 7);
    /** A concentrator mill line: grinding drums and flotation cells, the floor one charge wrecks. */
    private static final RoomShape MILL_LINE = RoomShape.rectangle(12, 8);
    /** The smelter: the furnace hall the concentrate is fired in, its burners and stacks. */
    private static final RoomShape SMELTER_HALL = RoomShape.rectangle(16, 10);
    /** The loadout, where the metal leaves for the planet. */
    private static final RoomShape LOADOUT_SHED = RoomShape.rectangle(10, 6);

    /**
     * Yard a mine's works keeps per mill line, on top of its packing slack: room for the ore line's runs between the
     * halls and the haul ground round them. UNTUNED.
     */
    static final int YARD_PER_MILL = 200;

    /** The room each step of a mine's processing chain is, in the order the ore passes through them. */
    public static final RoomPurpose RECEIVING = RoomPurpose.LOADING_BAY;
    public static final RoomPurpose STOCKPILE = RoomPurpose.STOCKROOM;
    public static final RoomPurpose MILL = RoomPurpose.PARTS_CAGE;
    public static final RoomPurpose SMELTER = RoomPurpose.ENGINE_ROOM;
    public static final RoomPurpose LOADOUT = RoomPurpose.VEHICLE_BAY;
    /** The chain in the order the ore passes. */
    public static final List<RoomPurpose> CHAIN = List.of(RECEIVING, STOCKPILE, MILL, SMELTER, LOADOUT);

    // The head works stands further in and its ore yards with it, so the deep end is the pit's.
    static final MapPlacement WORKS_AT = new MapPlacement(0.56f, 0.55f, 0.68f, 0.75f);
    static final MapPlacement INDUSTRY_AT = new MapPlacement(0.38f, 0.50f, 0.48f, 0.80f);

    @Override public String key() { return "mining"; }

    @Override public Set<String> industryIds() { return Set.of("mining", "techmining"); }

    @Override public float worksFit() { return RaidSiteTemplate.MINE_FIT; }

    @Override public MapPlacement worksAt() { return WORKS_AT; }

    @Override public MapPlacement industryAt() { return INDUSTRY_AT; }

    @Override public boolean yards() { return false; }

    @Override public GrownTrunkPlan.Profile industryGrowth() { return RaidSiteTemplate.SPARSE_GROWTH; }

    /** The deep end is the pit's: the works' only feeder is the outer flank's. */
    @Override public boolean deepFeeder() { return false; }

    /**
     * A mine's head works as an ore-processing chain ({@code raid-site-templates.md}; {@code voids.md}, the open pit):
     * the receiving house the ore line from the pit lands on (in the rear, toward the pit at the deep end, so the line
     * need not wrap round the compound), the stockpile shed, one concentrator mill line per machine (the machines a
     * raid wrecks), the smelter whose burners fire the concentrate, the loadout by the gate, and the posts its gates
     * are watched from. The ore line links them in that order. No keep and no airfield: the garrison beside it holds
     * the market.
     *
     * @param mills concentrator mill lines in the head works, one per machine
     */
    @Override public FortressProgram works(int mills) {
        if (mills < 1) throw new IllegalArgumentException("a mine has at least one mill line");
        return new FortressProgram(List.of(
                new FortressBuilding(RECEIVING, RECEIVING_HOUSE, Ward.REAR, 1),
                new FortressBuilding(STOCKPILE, STOCKPILE_SHED, Ward.YARD, 1),
                new FortressBuilding(MILL, MILL_LINE, Ward.YARD, mills),
                new FortressBuilding(SMELTER, SMELTER_HALL, Ward.YARD, 1),
                new FortressBuilding(LOADOUT, LOADOUT_SHED, Ward.FRONTAGE, 1),
                RaidWorksParts.guardPosts(2)),
                0, mills * YARD_PER_MILL, true);
    }

    /** The open pit beside the head works, at the deep end, stopped short of a storm bank's ground. */
    @Override public void sitePieces(SiteFrame frame, List<Piece> out) {
        Precinct head = frame.works();
        if (head == null) return;
        Site pit = RaidSiteTemplate.pitSite(head.seedX(), head.seedY(),
                RaidSiteTemplate.worksReach(worksFit(), frame.security(), frame.width(), frame.height()),
                frame.mirrorX(), frame.mirrorY(), frame.width(), frame.height());
        out.add(frame.bankWind() != null
                ? RaidSiteTemplate.clearOfBank(pit, frame.bankWind(), frame.width(), frame.height()) : pit);
    }
}
