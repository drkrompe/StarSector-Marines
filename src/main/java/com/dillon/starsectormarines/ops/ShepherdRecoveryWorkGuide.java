package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

record ShepherdRecoveryWorkGuide(ShepherdRecoveryProgress progress,OpeningBroker.Destination destination,Runnable plotCourse) implements MarkupPropertySource {
    public Object markupProperty(String property){return switch(property){
        case "classes"->"opening-guide console-readout";
        case "crest","portrait"->OpeningBroker.CREST;
        case "broker"->ShepherdRecoveryCampaign.NAME;
        case "affiliation"->"Independent cooperative";
        case "heading"->ShepherdRecoveryCampaign.TITLE;
        case "mission"->progress.departed()?"Morrow / registry transferred":progress.repaired()?"NEW / Working Passage":progress.claimSecured()?"NEW / Cold Start":"NEW / Salvage Rights";
        case "payment"->progress.departed()?"Named fleet hull / transfer completed":progress.repaired()?"Morrow + willing working crew / in kind":progress.claimSecured()?"22,000 cr / repair contract":"18,000 cr + protected recovery claim";
        case "location"->destination==null?"Asharu / Corvus":destination.label();
        case "route"->progress.departed()?"Yard reopened. Morrow joined the fleet.":"Marine operations > Tamsin Vale";
        case "preparation"->progress.departed()?"Vale keeps the Colossus and yard. Our contract is paid."
                :"Neutral crews in Hegemony space. Earn Morrow; Vale keeps the Colossus. Two squads recommended. Replenish first.";
        case "courseDisabled"->destination==null;
        case "courseAction"->plotCourse;
        default->throw new IllegalArgumentException("Unknown recovery guide property: "+property);
    };}
}
