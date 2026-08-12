# Campaign loot — post-battle salvage

> The consumer of the salvage *entitlement* the contract layer grants.
> [`../contracts/overview.md`](../contracts/overview.md) §"Salvage rights"
> defines the three-layer model that decides *how much* the player is
> owed (per-type baseline → negotiation knob → captain/fleet modifiers).
> This doc owns what happens *after* the battle: the item pool, the
> picker screen, and cargo-capacity interaction.

This is the MechWarrior Mercenaries appeal made concrete — the
post-battle screen where the haul turns into kept gear or fenced cash.
Highest user value of any campaign next-up; gated only by the loot UI,
since the entitlement is already plumbed end-to-end through
`MissionResolver` (`outcome.salvageEntitlement` lands on victory).

## Delivery slices

1. **Recovery manifest foundation** — deterministically roll a faction- and
   industry-flavored pool, freeze it beside the mission outcome, and expose its
   item count/value budget on the debrief. See
   [`stories/s1-recovery-manifest.md`](stories/s1-recovery-manifest.md).
2. **Picker screen** — item grid, selection budget, details/hover treatment,
   and debrief routing. Code-complete; see
   [`stories/s2-picker-screen.md`](stories/s2-picker-screen.md).
3. **Cargo settlement** — capacity preview, transfer selected stacks, and fence
   overflow at 75% base value.
4. **Rare recovery** — AI-core mission gates, `SALVAGE_EXPERT` high-value roll,
   Salvage Rig/Gantry multipliers, then blueprint/tech-recovery support.

## Recoverable categories

What can actually drop:

- **Vanilla weapons** — cargo-item weapons usable by player fleet
  ships. Pool drawn from the enemy's deployed ships.
- **Vanilla resources** — supplies, fuel, marines (yes, marines as a
  recovered resource — turncoats / freed POWs), heavy armor.
- **AI cores** — rare; specific mission types only (decapitation
  strikes against AI-using factions).
- **Blueprints / mod specs** — rare; gated by `tech-recovery` marine
  trait.
- **Future: mod-custom items** — design pluggable so we can add
  marine gear, faction-specific items later without touching the
  recovery system.

## Post-battle loot selection screen

MechWarrior Mercenaries-style screen, fires after mission resolution
when the player has any salvage entitlement:

- Grid of recovered items with icons, descriptions, values.
- Player picks items to keep within their entitlement.
- Cargo capacity check — if the player's fleet can't carry the
  entitlement, items beyond capacity convert to discount cash (75%
  market value, per [`../economy.md`](../economy.md)'s "fence on the
  spot" pattern).
- Cancel = forfeit remaining items (they don't make it home).

The captain `SALVAGE_EXPERT` trait and deployed-fleet Salvage Rig
modifiers (contract §"Layer 3") scale the recovered tonnage *before*
the player sees the grid — by loot time the entitlement is final.

## Current hook in code

`MissionResolver.compute()` already populates `salvageEntitlement` on
the `MissionOutcome` on victory, and `ResultsScreen` shows the final
percentage. There is no item pool / item roll / picker grid yet — that
is this feature's work. See the "What this loop does NOT do yet" note
in [`../contracts/complete/contracts-loop.md`](../contracts/complete/contracts-loop.md).

## Open questions

- Item-roll determinism — seed the recovered-item pool from
  (mission id, outcome) so save/reload of a results screen is stable?
- Fence-on-spot vs. carry — does auto-conversion at 75% undercut the
  "salvage > cash" axiom? Player choice: take less so it all fits, or
  take more and accept the discount (mirrors contract §"Open questions" 7).
- Salvage Rig detection scope — deployed fleet only, or any fleet in
  system (contract §"Open questions" 8).

## Decisions locked for the first vertical

- The roll is seeded from the immutable mission/outcome facts, so reopening or
  rebuilding the debrief cannot reroll it.
- `salvageEntitlement` is a percentage of the rolled pool's base value. The
  manifest freezes both the total pool value and the resulting selection-value
  budget; widgets do not own economy math.
- The recovery pool's target value is deliberately expressed as a small set of
  tunable multipliers by mission type and risk. This is balance scaffolding, not
  a permanent economy promise.
- The first slice recovers commodities and weapons. Rare cores and blueprints
  stay out until their mission/trait gates are representable without UI guesses.

## Related

- [`../contracts/overview.md`](../contracts/overview.md) — the salvage
  entitlement model upstream of this screen.
- [`../economy.md`](../economy.md) — fence-on-the-spot discount, the
  cash side of the take-it-or-leave-it choice.
- See also: [backgrounds](../backgrounds.md).
