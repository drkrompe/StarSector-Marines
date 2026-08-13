package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.CommandPower;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Enumerates the actual player-fleet members that can source command powers. */
public final class PlayerFleetPowerSources {

    private PlayerFleetPowerSources() {}

    /** One independently committable fleet member and all powers it supplies. */
    public static final class SourceShip {
        public final FleetMemberAPI member;
        public final String memberId;
        public final String shipName;
        public final String spriteName;
        public final List<CommandPower> powers;

        private SourceShip(FleetMemberAPI member, List<CommandPower> powers) {
            this.member = member;
            this.memberId = member.getId();
            this.shipName = member.getShipName() != null ? member.getShipName() : "Support ship";
            this.spriteName = member.getHullSpec() != null ? member.getHullSpec().getSpriteName() : null;
            this.powers = Collections.unmodifiableList(new ArrayList<>(powers));
        }
    }

    /** Power-bearing members in fleet order. Empty outside campaign. */
    public static List<SourceShip> committableShips() {
        if (Global.getSector() == null) return Collections.emptyList();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return Collections.emptyList();

        List<SourceShip> out = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            List<CommandPower> powers = PowerCatalog.contributedBy(member);
            if (!powers.isEmpty()) out.add(new SourceShip(member, powers));
        }
        return out;
    }
}
