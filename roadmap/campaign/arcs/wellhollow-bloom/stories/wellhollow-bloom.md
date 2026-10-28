# The Wellhollow Bloom: remaining work

Status: IN PROGRESS

Written: 2026-10-27

The arc is reachable: after Morrow departs (A Working Ship), Asharu's board lists
Hollis Maro (`WellhollowBloomCampaign`), whose rows are the current
chapter and a replay of every chapter won (`WellhollowBloomProgress`). Colony
Breach launches through the rescue path; the Cordon and heart chapters build
their fixtures on the ARID pool maps. What remains:

1. **Owner picks** (`wellhollow-bloom-arc.md`, *Open*): replay pay, art and
   voice. Change the built recommendation if a
   pick differs: the order is `WellhollowBloomProgress.Chapter`, the replay fee
   `Chapter.replayFee`, the gate `WellhollowBloomCampaign.current`.
2. **Live acceptance.** Play all four chapters in a launched campaign: the
   Maro appears at Asharu, each victory unlocks the next row, a loss briefs as
   a retry, a replay pays half and leaves the arc where it was, and the ledger
   survives save and reload.
3. **Evidence as shipped.** One `commanderEvidence` pass per chapter on its
   ARID map, judged by behaviour (the basin's woods harvested and burned where
   the chapter says, the allies holding), balanced against a player.
4. **Colony Breach's report.** The story chapter is `MissionSource.STORY`, so
   the outcome does not carry the rescue's evacuee and mother-bore fields
   (`MissionOutcome` reads them only for rescue sources). Carry them so the
   debrief can say how many came out.
5. **Voice.** Briefing narration for Maro; the portrait and location
   banners are in, pending the owner's review of the art.
