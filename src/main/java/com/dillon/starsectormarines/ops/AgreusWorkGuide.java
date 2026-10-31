package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusDistrictEscortProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/** Optional referral, custody decision and completed record in the existing HQ correspondence tile. */
record AgreusWorkGuide(AgreusArcProgress progress, OpeningBroker.Destination destination, Runnable plotCourse) implements MarkupPropertySource {
    public Object markupProperty(String property) { return switch (property) {
        case "classes" -> "opening-guide console-readout";
        case "crest", "portrait" -> OpeningBroker.CREST;
        case "broker" -> AgreusArcCampaign.NAME;
        case "affiliation" -> Strings.get("agreusGuideAffiliation");
        case "heading" -> Strings.get("agreusArcTitle");
        case "mission" -> progress.yardCompleted() ? handoverMission()
                : Strings.get(progress.warrantCompleted() ? "agreusGuideMissionYard" : progress.completed() ? "agreusGuideMissionWarrant" : "agreusGuideMissionWater");
        case "payment" -> progress.yardCompleted() ? handoverPayment()
                : Strings.get(progress.warrantCompleted() ? "agreusGuidePaymentYard"
                : progress.completed() ? "agreusGuidePaymentWarrant" : "agreusGuidePaymentWater");
        case "location" -> destination == null ? Strings.get("agreusGuideLocationFallback") : destination.label();
        case "route" -> progress.yardCompleted() ? handoverRoute()
                : destination == null ? AgreusArcCampaign.accessReason() : Strings.format("agreusGuideRouteRusk",
                progress.warrantCompleted() ? Strings.get("agreusArcMissionYardOwners") : progress.completed() ? Strings.get("agreusArcMissionLimitedWarrant") : Strings.get("agreusGuideRouteNorthWeir"));
        case "preparation" -> progress.yardCompleted() ? handoverPreparation()
                : Strings.get(progress.warrantCompleted() ? "agreusGuidePrepYard"
                : progress.completed() ? "agreusGuidePrepWarrant" : "agreusGuidePrepWater");
        case "courseDisabled" -> destination == null;
        case "courseAction" -> plotCourse;
        default -> throw new IllegalArgumentException("Unknown Agreus guide property: " + property);
    }; }

    private String handoverMission() {
        return switch (progress.handoverStage()) {
            case OPEN -> Strings.get("agreusGuideHandoverOpen");
            case SIGNED -> Strings.get("agreusGuideHandoverSigned");
            case RATIFIED -> progress.smallWarSettled() ? Strings.format("agreusGuideHandoverRatifiedSettled", DistrictEscortCampaign.name()) : Strings.get("agreusGuideHandoverRatified");
            case DECLINED -> Strings.get("agreusGuideHandoverDeclined");
            case NOT_REACHED -> Strings.get("agreusGuideHandoverComplete");
        };
    }
    private String premiumSuffix() {
        return Strings.get(progress.custodyPremiumDue() > 0 ? "agreusGuidePremiumDue" : "agreusGuidePremiumForgone");
    }
    private String handoverPayment() {
        return switch (progress.handoverStage()) {
            case SIGNED -> Strings.format("agreusGuidePaySigned", agreementName(progress.signedAgreement()), premiumSuffix());
            case RATIFIED -> (progress.smallWarSettled()
                    ? Strings.format("agreusGuidePaySettled", AgreusDistrictEscortProgress.PAYMENT, agreementName(progress.signedAgreement()),
                    progress.smallWarPremiumPaid() > 0 ? Strings.get("agreusGuidePremiumPaid") : "")
                    : Strings.format("agreusGuidePayRatified", agreementName(progress.signedAgreement()), premiumSuffix()));
            default -> Strings.get("agreusGuidePayDefault");
        };
    }
    private String handoverRoute() {
        return switch (progress.handoverStage()) {
            case OPEN -> Strings.get("agreusGuideRouteOpen");
            case SIGNED -> Strings.format("agreusGuideRouteSigned",
                    Strings.get(progress.signedAgreement() == AgreusYardAgreement.HOUSE_CUSTODY ? "agreusGuideLaterRusk" : "agreusGuideLaterLenna"));
            case RATIFIED -> progress.smallWarSettled()
                    ? Strings.format("agreusGuideRouteSettled", progress.signedAgreement() == AgreusYardAgreement.HOUSE_CUSTODY
                    ? AgreusArcCampaign.NAME : DistrictEscortCampaign.LENNA_NAME, DistrictEscortCampaign.name())
                    : Strings.get("agreusGuideRouteRatified");
            default -> Strings.get("agreusGuideRouteDefault");
        };
    }
    private String handoverPreparation() {
        return switch (progress.handoverStage()) {
            case OPEN -> Strings.get("agreusGuidePrepOpen");
            case RATIFIED -> progress.smallWarSettled()
                    ? Strings.format("agreusGuidePrepSettled", AgreusArcCampaign.VOSS_LAST.speaker(), AgreusArcCampaign.VOSS_LAST.text())
                    : Strings.get("agreusGuidePrepRatified");
            case SIGNED -> Strings.format("agreusGuidePrepSigned", AgreusArcCampaign.VOSS_WARNING.speaker(), AgreusArcCampaign.VOSS_WARNING.text());
            case DECLINED -> Strings.get("agreusArcDeclinedRecord");
            case NOT_REACHED -> Strings.get("agreusGuidePrepComplete");
        };
    }
    static String agreementName(AgreusYardAgreement agreement) {
        return Strings.get(agreement == AgreusYardAgreement.HOUSE_CUSTODY ? "agreusDeskTabCustody" : "agreusDeskTabCompact");
    }
}
