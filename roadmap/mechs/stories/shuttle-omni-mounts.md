# Story — Shuttle weapon mounts are omni slots

Status: PROPOSED

Written: 2026-10-28

Read `mechs-nouns.md` (a vehicle's gun card) and the boat refit design before changing this story.

## Problem

A vehicle's gun card is typed: it takes the mech weapons of the type and size its turret was built for. A shuttle's
weapon mounts are different in kind. The Store already sells shuttle Weapons in the shared vocabulary of parts, and the
owner's ruling (2026-10-28) is that a shuttle mount is **truly omni**: any shared weapon fits it, with no hardpoint
type to match.

## Goal

Each shuttle weapon mount is modelled as an omni slot (`SocketType.OMNI`, sized as its mount is) and takes any shared
weapon the company owns, fitted and stripped from stores like a vehicle's gun, with the derived-mount rule the vehicle
gun card uses (`VehicleGun`) where a mount needs the weapon's own muzzle and effects.

## Not decided

The footprint each mount accepts, how a mounted weapon is drawn on an airframe, and whether an omni mount changes
what a shuttle's loadout costs in lift. These are the owner's to settle when the story is played.
