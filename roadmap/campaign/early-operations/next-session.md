# Early operations — next session

## Playtest queue

- Verify the relief line survives long enough for one slow Aeroshuttle arrival
  without winning the battle unattended.
- Verify one starting transport and one selected fireteam satisfy the first
  briefing, while the counterattack communicates its two-sortie requirement.
- Tune the 8-allied / 12-bandit relief and 4-allied / 12-bandit counterattack
  ratios from live results rather than adding heavier units.
- Confirm the two missions remain visible and retryable after a failed battle,
  then disappear exactly once after victory.

## The change that moves these ratios — half landed

The `company-view` track changes the size of the unit these missions
deploy.

- **Shipped 2026-08-22.** A player squad is now **twelve** marines in three
  four-marine fire teams (`c7-organization-and-ranks.md`), because
  progression S1's shipped 9x lethality scale makes a six-marine squad
  combat-ineffective within seconds of contact. The officer ladder now caps
  command in squads and the company starts under a Lieutenant with one full
  squad of twelve, where it used to start with ten marines in two teams.
- **Still pending.** Transport capacity denominated in fire teams
  (`c8-lift-capacity-and-multi-pass-drops.md`): Hermes 3 → 4, the 4-seat
  hulls unchanged, Tarsus/Buffalo/Mule/Nebula 5–7 → 8, Valkyrie 8 → 12. A
  squad therefore arrives across one to three passes and holds at its LZ
  until it has formed up.

The decision was to take those changes and re-tune the opening ladder
around them, rather than tune against numbers we intend to replace.

So: the relief and counterattack ratios above are still worth playing for
*feel* (does the line hold, is the battle the right length), but do not
freeze the 8+4 vs 12 and 4+player vs 12 numbers until C8 has landed too —
the arrival *schedule* is the half that is still moving, and a squad that
trickles in four at a time fights nothing like one that lands whole.

## Next implementation candidate

Contract a pinned-assault relief variant only after the opening ladder's force
ratios and battle length are accepted in playtest.
