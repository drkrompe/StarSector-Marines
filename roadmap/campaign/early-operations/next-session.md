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

## Incoming change that moves these ratios

`company-view` [C8](../../company-view/stories/c8-lift-capacity-and-multi-pass-drops.md)
raises every transport's marine capacity to a floor of one whole six-marine
squad — Aeroshuttle/Kite go 4 → 6, Hermes 3 → 6, Tarsus 5 → 6. That is a
50–100% increase in early-game lift, and it was accepted deliberately: the
decision (2026-08-22) is to take the capacity change and re-tune the
opening ladder around it, rather than tune against seat counts we intend to
replace.

So: the relief and counterattack ratios above are still worth playing for
*feel* (does the line hold, is the battle the right length), but do not
freeze the 8+4 vs 12 and 4+player vs 12 numbers until C8 has landed.

## Next implementation candidate

Contract a pinned-assault relief variant only after the opening ladder's force
ratios and battle length are accepted in playtest.
