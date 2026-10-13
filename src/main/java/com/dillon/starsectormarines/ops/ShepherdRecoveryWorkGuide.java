package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

record ShepherdRecoveryWorkGuide(ShepherdRecoveryProgress progress,OpeningBroker.Destination destination,Runnable plotCourse) implements MarkupPropertySource {
    public Object markupProperty(String property){return switch(property){
        case "classes"->"opening-guide console-readout";
        case "crest","portrait"->OpeningBroker.CREST;
        case "broker"->ShepherdRecoveryCampaign.NAME;
        case "affiliation"->"Basin Reclamation Cooperative";
        case "heading"->ShepherdRecoveryCampaign.TITLE;
        case "mission"->progress.repaired()?"Morrow repaired / departure pending":progress.claimSecured()?"NEW / Cold Start":"NEW / Salvage Rights";
        case "payment"->progress.repaired()?"22,000 cr paid / hull remains grounded":progress.claimSecured()?"22,000 cr / repair contract":"18,000 cr + protected recovery claim";
        case "location"->destination==null?"Asharu / Corvus":destination.label();
        case "route"->progress.repaired()?"Repairs signed off. Working Passage is not yet available.":"Marine operations > Tamsin Vale";
        case "preparation"->"Two squads recommended. Optional owned Hound. Replenish first; this work can wait.";
        case "courseDisabled"->destination==null;
        case "courseAction"->plotCourse;
        default->throw new IllegalArgumentException("Unknown recovery guide property: "+property);
    };}
}
