# Story: context-gated AI-core recovery

**Status:** CODE COMPLETE — in-game smoke pending (2026-08-12)

## Goal

Add rare AI cores to the deterministic recovery catalog without making every
ground operation a source of endgame colony items.

## Locked rules

- Only `ASSAULT`, `RAID`, and `SABOTAGE` can expose cores.
- The target must be AI-linked: Remnants or Tri-Tachyon, or a tech-mining,
  orbital-works, or high-command facility.
- Gamma cores are eligible at any risk, beta at medium/high risk, and alpha at
  high risk only.
- Each core is a single rare commodity stack. Alpha/beta/gamma weights are
  0.03/0.12/0.30 respectively.
- Cores use vanilla commodity names/icons/cargo space. Their fallback selection
  values are Cr. 150,000/30,000/10,000 if a content registry reports no price.
- Omega cores and blueprints remain out of scope.

## Acceptance

- Eligibility is a pure, directly tested mission/context/risk rule.
- Ineligible mission types and ordinary targets add no core candidates.
- Eligible candidates flow through the existing deterministic picker and
  commodity settlement path.
- Full build green; in-game icon/value/cargo smoke remains the shipping gate.

## Automated verification

- `AiCoreLootRulesTest` covers risk-tier unlocks, faction and industry context,
  ordinary-target exclusion, non-strike exclusion, and null safety.
- `gradlew.bat build` passes on 2026-08-12.
