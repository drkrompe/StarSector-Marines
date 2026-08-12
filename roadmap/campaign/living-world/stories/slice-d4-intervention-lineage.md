# Slice D4a — Counter-offer chain lineage

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Represent “this contract opposes that hostile plot” without overloading the
contract's beneficiary/parent chain relationship.

## Audit result

- `contractChainId` is documented as the parent chain whose missions advance
  its political play. It is persisted and compacted but has no runtime reader
  yet; future completion wiring still needs that unambiguous meaning.
- `contractSourceContractId` is contract-to-contract lineage used by generated
  followups such as stationing extraction, not chain opposition.

## Locked rules

- New `contractOpposedChainId` stores the hostile source chain for an
  intervention; ordinary contracts use `-1`.
- `addContract` always initializes the opposed-chain reference to `-1`; a
  counter-offer generator binds it explicitly after append.
- Capacity growth, legacy backfill, and maintenance compaction preserve the
  column and its sentinel.
- No mission-resolution behavior changes in this schema hunk.

## Automated verification

- `CampaignStateStationingColumnsTest` covers growth and legacy backfill.
- `ContractTableCompactorTest` locks opposed-chain alignment through compaction.
