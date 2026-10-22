# Cordon campaign event

Status: PLANNED

Written: 2026-10-22

Model: `bloom-mission-arc.md` (Tie to the campaign); `campaign-event-nouns.md`.

## Scope

The bloom's second call: `BLOOM_CORDON` appended to `CampaignEventType`, raised
by `RavBloomSystem`'s daily tick when a bloom goes dark (the Road) or settles
starved (the Line); its frozen commitment (variant, line posture, inherited
bores and their tiers from the Colony Breach report); a mission factory on
`PolityDefenceMissionFactory`'s model; the briefing (variant, posture); a
resolver that writes the report (people out, redoubts held, convoys lost,
bores collapsed) back to the bloom's arc record exactly once; the clock hold
while the beat is committed.
