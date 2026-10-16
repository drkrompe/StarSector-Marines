# Compatibility entry point: the registered live/wreck hauler exporter owns runtime art.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'hauler/pack.ps1')
