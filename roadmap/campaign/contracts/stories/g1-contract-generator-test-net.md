# G1 — ContractGenerator test net

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Freeze the shipped STRIKE generator's deterministic offer shape and caps before
adding rank-gated non-STRIKE types.

## Acceptance

- Same campaign rows and day produce identical offers.
- Generated STRIKE rows retain their one-phase, Cr. 25,000, 60%-salvage shape.
- An existing patron offer prevents a second offer for that patron.
- Twenty sector-wide open offers stop generation.
- Full build green.

## Verification

- `ContractGeneratorTest` covers all acceptance points above.
- `gradlew.bat build` passes on 2026-08-12.
