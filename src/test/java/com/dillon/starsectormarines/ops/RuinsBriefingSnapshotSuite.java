package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsSurvey;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrizes;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A ruins expedition's briefing for each occupant, read through that occupant's own survey pool
 * ({@code ruins-expedition.md}, Briefing), 1920x1080 at 100%. It shows each pool directly; in play, an occupant whose
 * side does not fight yet reads as the scavengers it stands in for ({@code RuinsExpeditionMissionFactory.surveyPool}).
 * Each ends on the vault's tell ({@code ruins-expedition.md}, The vault and its prize) for a showcase prize that
 * occupant may hold ({@link #showcase}).
 */
public final class RuinsBriefingSnapshotSuite implements SnapshotSuite {

    private static final String PLANET = "Ossuary";
    private static final String MARKET = "ossuary_market";
    private static final long SECTOR_SEED = 7L;

    public String id() { return "ruins-briefing"; }
    public String label() { return "Ruins expedition briefing / one per occupant"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        var result = new ArrayList<SnapshotArtifact>();
        try (var strings = new HeadlessStrings(context.modRoot(), text -> text)) {
            HeadlessSvgAssets.install(context.modRoot());
            HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
            for (RuinsOccupant occupant : RuinsOccupant.values()) {
                // Only a scattered site holds nobody; the others read at a mid grade.
                RuinsGrade grade = occupant == RuinsOccupant.NOBODY ? RuinsGrade.SCATTERED : RuinsGrade.EXTENSIVE;
                Mission mission = Mission.builder()
                        .name(Strings.format("ruinsExpeditionMissionName", PLANET))
                        .type(MissionType.RUINS)
                        .source(MissionSource.RUINS_EXPEDITION)
                        .tier(RuinsExpeditionMissionFactory.tierFor(grade))
                        .risk(RiskLevel.MEDIUM)
                        .dialogue(RuinsExpeditionMissionFactory.briefing(PLANET,
                                RuinsSurvey.read(RuinsSurvey.poolFor(occupant), MARKET, grade),
                                RuinsVaultPrizes.tellKeys(SECTOR_SEED, MARKET, showcase(occupant))))
                        .build();
                Map<String, Object> props = BriefingViewModel.campaignPreviewProps(false);
                props.put("missionTitle", mission.name);
                props.put("missionFlavor", "");
                MissionDialogue.put(props, "mission", mission);
                // As the screen fills them for an expedition: no contract and no employer, the occupant opposing.
                BriefingViewModel.putReward(props, mission);
                BriefingViewModel.putFactionIdentity(props, "employer",
                        Strings.get("briefingVmEmployerUndisclosed"), null);
                BriefingViewModel.putFactionIdentity(props, "opposition", oppositionName(occupant), null);
                BriefingViewModel.withEmptyStyles(props);
                result.add(new SnapshotArtifact(
                        "ruins-briefing-" + occupant.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".png",
                        UiSnapshotSuite.renderMissionBriefing(context, renderer, 1920, 1080, props, 1f, false, null)));
            }
        }
        return result;
    }

    /** A prize each occupant may hold, one per family across the set, so every kind of tell is shown. */
    static RuinsVaultPrize showcase(RuinsOccupant occupant) {
        return switch (occupant) {
            case NOBODY -> new RuinsVaultPrize(RuinsVaultPrize.Family.GOODS, RuinsVaultTheme.DEPOT, null, 0);
            case SCAVENGERS -> item(RuinsVaultTheme.CRYOARITHMETIC_ENGINE);
            case RAV -> item(RuinsVaultTheme.SOIL_NANITES);
            case DOMAIN_SECURITY -> new RuinsVaultPrize(RuinsVaultPrize.Family.AI_CORE, RuinsVaultTheme.SERVER_HALL,
                    RuinsVaultPrize.BETA_CORE, 2);
            case CONTINGENCY_SECURITY -> new RuinsVaultPrize(RuinsVaultPrize.Family.TECH, RuinsVaultTheme.ARMORY,
                    null, 1);
        };
    }

    private static RuinsVaultPrize item(RuinsVaultTheme theme) {
        return new RuinsVaultPrize(RuinsVaultPrize.Family.COLONY_ITEM, theme, theme.itemId, 2);
    }

    /** The opposing faction's display name, as the screen reads it off the campaign faction; headless has none. */
    private static String oppositionName(RuinsOccupant occupant) {
        return switch (occupant) {
            case SCAVENGERS -> "Pirates";
            case DOMAIN_SECURITY -> "Derelict";
            case CONTINGENCY_SECURITY -> "Remnant";
            case NOBODY, RAV -> Strings.get("briefingVmOppositionUnidentified");
        };
    }
}
