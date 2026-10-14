package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

record ShepherdRecoveryWorkGuide(ShepherdRecoveryProgress progress,OpeningBroker.Destination destination,Runnable plotCourse) implements MarkupPropertySource {
    public Object markupProperty(String property){return switch(property){
        case "classes"->"opening-guide console-readout";
        case "crest","portrait"->OpeningBroker.CREST;
        case "broker"->ShepherdRecoveryCampaign.NAME;
        case "affiliation"->Strings.get("shepherdGuideAffiliation");
        case "heading"->ShepherdRecoveryCampaign.TITLE;
        case "mission"->progress.departed()?Strings.get("shepherdGuideComplete"):progress.repaired()?Strings.get("shepherdGuidePassage"):progress.claimSecured()?Strings.get("shepherdGuideCold"):Strings.get("shepherdGuideSalvage");
        case "payment"->progress.departed()?Strings.get("shepherdGuideTransferred"):progress.repaired()?Strings.get("shepherdGuidePassagePayment"):progress.claimSecured()?Strings.get("shepherdGuideColdPayment"):Strings.get("shepherdGuideSalvagePayment");
        case "location"->destination==null?Strings.get("shepherdGuideLocation"):destination.label();
        case "route"->progress.departed()?Strings.get("shepherdGuideClosedRoute"):Strings.get("shepherdGuideRoute");
        case "preparation"->progress.departed()?Strings.get("shepherdGuideClosedPreparation")
                :progress.repaired()?Strings.get("shepherdGuidePassagePreparation"):progress.claimSecured()?Strings.get("shepherdGuideColdPreparation"):Strings.get("shepherdGuideSalvagePreparation");
        case "courseDisabled"->destination==null;
        case "courseAction"->plotCourse;
        default->throw new IllegalArgumentException("Unknown recovery guide property: "+property);
    };}
}
