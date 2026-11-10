package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;

import java.util.List;
import java.util.Set;

/**
 * One industry's Raid site ({@code raid-mission.md}, Site templates): what its works compound holds, where it and
 * its industrial district stand, the ground it lays beside the works, and how it dresses the works once built. The
 * shared site ({@link RaidSiteTemplate}) lays the landing, the homes, the garrison, the roads and the walls the same
 * for every industry and asks the industry for the rest; each industry is a class of its own
 * ({@link RaidIndustries#ALL}), so one industry's work never touches another's.
 *
 * <p>Every default is heavy industry's, the first site the owner accepted.
 */
public interface RaidIndustry {

    /** The site's name, for evidence, the briefing and the plan ({@link PrecinctPlan#raidIndustry()}). */
    String key();

    /** The market industry ids this site is laid for. */
    Set<String> industryIds();

    /**
     * The works compound's program for a raid on this many machines. Its machines are its
     * {@code RoomPurpose.PARTS_CAGE} rooms, one building each, which is how the raid finds them; a works is
     * {@link FortressProgram#detached() detached} so every machine stands as a building of its own.
     */
    FortressProgram works(int machines);

    /** Share of the map the works may claim before its program is trimmed. */
    default float worksFit() { return RaidSiteTemplate.WORKS_FIT; }

    /** Where the works stands, in the canonical template (landing west, works north); mirrored by the seed. */
    default MapPlacement worksAt() { return RaidSiteTemplate.WORKS_AT; }

    /** Where the industrial district stands between the homes and the works, in the canonical template. */
    default MapPlacement industryAt() { return RaidSiteTemplate.INDUSTRY_AT; }

    /** Whether the industrial district's yards stand beyond the works at the deep end. */
    default boolean yards() { return true; }

    /** How the industrial district (and its yards) grows its streets. */
    default GrownTrunkPlan.Profile industryGrowth() { return RaidSiteTemplate.INDUSTRY_GROWTH; }

    /** What kind of district stands round the works. */
    default PrecinctCharacter industryCharacter() { return PrecinctCharacter.INDUSTRY; }

    /**
     * Whether a feeder line runs off the map from the works' deep gate, as well as the outer flank's. False where the
     * deep end holds the industry's own ground (a mine's pit).
     */
    default boolean deepFeeder() { return true; }

    /**
     * Ground and structures the industry lays on the plan's layout beside the shared site's roads: a mine's pit, a
     * port's landing apron. Called once the places are laid; {@code frame} says where the works stands and how the
     * site was turned.
     */
    default void sitePieces(SiteFrame frame, List<Piece> out) { }

    /**
     * Dresses the built works ({@code RaidWorksDressingStage}): the props, lines and yards that tell what the works
     * does, laid after the buildings, roads and doorways are final and before the landscape pass lays trees, hills
     * and the rest of the open country. {@code works} is the plan's objective; its buildings are the map's points of
     * interest inside its claim, their rooms' purposes the program's.
     */
    default void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) { }
}
