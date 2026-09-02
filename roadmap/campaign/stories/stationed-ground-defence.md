# Stationed ground defence: a Garrison that counts while you are away

Status: IN PROGRESS

Written: 2026-09-02

Updated: 2026-09-02 — slices 1–5 shipped (c4ed61b9c, 8db3fc966, c781a10a4,
da552abcc); the live pass remains. See "Decided while shipping".

Read `contracts-nouns.md` (stationing, response, settlement),
`meta-progression.md` (the vanilla seam), `personnel-nouns.md` (what a
detachment's strength is), and `campaign-framework-nouns.md` (campaign
systems and the external-state boundary) before implementing this story.

## Goal

A Garrison retainer buys availability, and today availability is worth
nothing unless the player is standing on the planet when the raid arrives.
Vanilla resolves the ground half of every raid as a strength ratio against the
market's ground defences, and the stationed detachment is invisible to that
ratio. Worse, the mod already arms a Garrison defence from a vanilla
fleet-group raid (`VanillaRaidGarrisonSystem`), and a defence the player wins
changes nothing in vanilla: `GarrisonDefenseResolution` settles the contract
and the raid lands anyway. So the company is hired to defend a place, defends
it, and the place is raided.

After this story a stationed detachment is a real ground-defence asset, in
three ways:

1. **It counts.** While stationed, it contributes to the market's vanilla
   ground-defence stat, so autoresolve, the raid's own intel assessment, and
   every vanilla raid shape feel it whether or not the player is present.
2. **A won defence stops the raid.** The fought response, on a win, ends the
   vanilla raid through vanilla's own abort or fail path, so the fleets go
   home and any listener consequence lands.
3. **An unanswered vanilla defence settles from what vanilla did.** A raid
   largely repelled by a garrison the player never commanded is a held
   contract with casualties, not a lapse.

Under the growth rule in `meta-progression.md`: produces Standing (a settled
Garrison) and consumes Reach (the detachment is bound for the term) and
Capability (casualties taken without the player's hand on them); first
reachable at Stage 2 with the first Garrison offer; counter-pressure is that a
detachment stationed on a place that is raided while the player is elsewhere
takes its losses from a formula, not a fight.

## What vanilla raids look like in 0.98a

Two shapes, both of which resolve their ground half through
`MarketCMD.getRaidEffectiveness` against `GROUND_DEFENSES_MOD`:

- **Fleet groups** (`GenericRaidFGI` with an `FGRaidAction` payload): hostile
  activity against the player's colonies, and the League, Diktat and
  Tri-Tachyon expeditions. Targets are markets in the player's colony system,
  so a patron market is only reached when it shares that system. Sent home
  through `FleetGroupIntel.abort`, which issues return assignments and fires
  the group's listener.
- **Raid intel** (`RaidIntel` with an action stage): pirate-base raids
  (`PirateRaidActionStage`), which pick a nearby system and weight its markets
  by size, and punitive expeditions (`PEActionStage`) against a colony
  industry. **This is the shape that actually reaches patron markets**, and
  the existing trigger does not see it. Sent home through
  `RaidIntel.forceFail`, with the stage's own straggler return path as the
  thing to verify.

Two other vanilla pressures are answered rather than covered: a Hegemony
inspection raids only when the player resists it, and its confiscation
effectiveness already reads the stat, so the contribution applies with no
further work while fighting a resisted inspection stays out of scope. A
blockade has no ground half and never will.

## Shape

**Stationed strength is a flat contribution, keyed by contract.** A campaign
system applies one flat modifier to the protected market's ground-defence stat
per active Garrison contract, with a stable modifier id derived from the
contract id and a description naming the company. The value is the living
seats of the stationed detachment times a per-seat worth that starts at
vanilla's own one-per-cargo-marine and scales by the squad's experience
standard, so twelve Veterans outweigh twelve cargo marines and a Green squad
does not. The initial ladder is a proposal for the probe, not a number the
docs own: Green 1, Regular 1.5, Veteran 2, Elite 3 per seat.

**The write is attributable and replay-safe.** The system reapplies daily
(overwriting the same id), removes the modifier when the contract leaves its
active states or the market cannot be resolved, and keeps a small persisted
set of applied (market, contract) pairs so a load or a compaction can sweep
what an earlier day applied. Vanilla persists the stat with the market, but
the sweep does not trust that.

**Both raid shapes feed one trigger.** The existing `VanillaRaidGarrisonSystem`
grows a second reader for `RaidIntel` raids and keeps its event-key scheme, so
a pirate-base raid on a patron market arms the Garrison defence the way a
fleet-group raid does. The threat snapshot it builds is the one the polity
story consumes; it gains the raid's ground strength and a handle that can end
the raid.

**A won defence ends the raid.** `GarrisonDefenseResolution` on `DEFENSE_WON`
for a vanilla-triggered defence ends the raid through the handle: abort for
the fleet group, force-fail for the raid intel. Ending is idempotent; a group
already ending is left alone. Rival strikes and internal flips are
mod-simulated and have nothing to end.

**An unanswered vanilla defence settles from vanilla's result.** Today an
unanswered response lapses to failure regardless. For a vanilla-triggered
defence the lapse system instead reads what happened: if the raid ended
without the market being flagged recently raided by the attacker, the
detachment held and the contract stays active; if the raid landed, the
outcome is graded on the raid effectiveness vanilla computed with the
detachment counted. Below vanilla's own "largely repelled" band the defence
is held with light casualties; above its "successful" band the garrison is
overrun and the assignment fails; between, held with heavy casualties. The
bands are vanilla's 0.33 and 0.66, so the intel's forecast and the settlement
agree. Casualties are applied through the ordinary personnel outcome path.

## Slices

1. **Strength contribution.** *Shipped.* The daily system, the per-seat worth read from
   the roster, the persisted applied set, and the sweep. Unit tests pin the
   value against a fake stat, the removal on every non-active state, and the
   sweep after a simulated load. A probe reports the assessed raid outcome of
   a minor and a major raid against a size-3 and a size-5 market with nothing,
   one Green squad, and two Elite squads stationed.
2. **Raid-intel reader.** *Shipped.* The second threat reader over `RaidIntel`, deriving
   targets the way the pirate stage does (system plus hostility) since its
   target list is protected, and reading the punitive expedition's target
   directly. Same event key, same arming.
3. **Ending the raid.** *Shipped, live verification pending.* The handle and its two implementations, an abortable
   seam the tests can fake, and the resolution calling it on a win. Verify
   live that `forceFail` alone sends stragglers home; if not, the handle calls
   the stage's return path.
4. **Absent settlement.** *Shipped.* The lapse system's vanilla branch, the grading, and
   the casualty application. Tests pin each band and that a rival-strike lapse
   is untouched.
5. **Attacker identity on the payload.** *Shipped.* `GarrisonDefenseMissionFactory`
   builds its mission with no defender-faction override, so today's raiders
   wear the defended market's own roster: pirates landing on a Hegemony world
   arrive in Hegemony kit. The payload already carries the attacker faction;
   the factory passes it as the override. One line, one test.
6. **Live pass.** *Remaining.* One pirate-base raid on a patron market with a stationed
   squad and the player elsewhere; one fought and won; one fleet-group raid
   on a patron market sharing the player's colony system. Read the raid
   intel's assessment before and after stationing, and check the raiders
   wear their own faction's kit.

## Decided while shipping

Choices the implementation made that the design did not settle. Each is a
first proposal for the live pass to judge, not a number the docs own.

- **The modifier does not name the company.** Nothing on `CampaignState` or
  the roster carries a company name (the roster knows only the flagship), so
  the description is "Stationed mercenary detachment". Naming it is new
  plumbing, and waits for a company identity to exist.
- **A fleet group's ground strength is its strongest fleet, not its sum.**
  `FGRaidAction.performRaid` lands one fleet at a time and grades each landing
  on that fleet alone, so the sum was a unit vanilla never uses.
- **A group that has failed without ending is left alone.** `abort()` fires
  the group's listener unconditionally, so the ender treats `isFailed()` as
  over, matching the reader that armed the defence.
- **Casualties for an absent defence.** A held defence loses half of what the
  raid effectiveness would have taken; an overrun one loses the effectiveness
  outright, capped at nine in ten so it is beaten rather than annihilated.
  Wounded recover on the fought Garrison's own clock. The people struck are
  seeded from the event key, so a replayed settlement names the same marines.
- **A raid with no strength estimate settles held-with-losses** at the middle
  of vanilla's uncertain band. Something landed and nothing says how hard.
- **Overrun is failed, not lapsed.** The garrison stood and was beaten, so the
  employer takes the fought-and-lost delta; a lapse would charge standing the
  player never spent. The captain comes home with that on their record.
- **A raid still in the air has no window to miss.** The response deadline
  carries the term's own expiry and stays unset for an open-ended term, which
  leaves the inbox's computed countdown showing a date that cannot fire. A term
  that runs out with the raid still coming settles held at no cost.
- **Landing is read off vanilla's recently-raided flag** keyed by the attacker,
  which every NPC raid path stamps. A flag left by an earlier raid by the same
  faction reads as a landing; vanilla itself skips a flagged market, so in the
  common case the second error cancels the first.
- **Unknown identity reads as repelled**, so a malformed vanilla defence
  settles held rather than failing the player.
- **The strength ladder probe** (one Green squad, two Elite squads, against a
  minor and a major raid on size-3 and size-5 markets) moved the assessed
  outcome only at the margins on Green 1 / Regular 1.5 / Veteran 2 / Elite 3.
  A lift of roughly one and a half to two times is the candidate for the live
  pass, judged against the intel's forecast on a size-3 colony.

## Acceptance

- A market with an active Garrison shows the company's contribution in its
  ground-defence breakdown, sized by living seats and experience standard,
  and loses it on the day the contract leaves its active states.
- A pirate-base raid on a Garrison market arms the defence.
- A won vanilla-triggered defence sends the raid home through vanilla's own
  path, exactly once. Whether `forceFail` alone gives a raid intel's
  stragglers return orders is still the live pass's question.
- An unanswered vanilla-triggered defence settles held, held-with-losses, or
  overrun from the raid effectiveness vanilla computed, with casualties on the
  roster; a rival-strike lapse still settles as it does today.
- The raid intel's own forecast changes when a Veteran squad is stationed on a
  size-3 colony.

## Out of scope

- A patron-less posting on the polity. It reuses every mechanism here and is a
  slice of `polity-defence-raid-hook.md`.
- Fighting a resisted Hegemony inspection.
- A Pather cell's sabotage, which has no fleet and is the mirror of the
  company's own Sabotage mission rather than a raid.
- Cadre contracts contribute nothing; they hold a detachment for training,
  not defence.

## Open questions

- Whether the contribution should also reduce the mod's own rival-strike and
  internal-flip pressure, or those stay purely political. Leaning: political.
- Whether a stationed detachment deters raids before they launch. The pirate
  stage weights targets by size only; the fleet-group planner does not consult
  ground defences. Deterrence would be a new vanilla write and is not in this
  story.
