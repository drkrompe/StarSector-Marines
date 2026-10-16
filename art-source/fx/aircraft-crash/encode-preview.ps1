param([string]$Ffmpeg='ffmpeg')
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$frames=Join-Path $root 'build/snapshots/aircraft-crash/frames/frame-%04d.png'
$output=Join-Path $PSScriptRoot 'crash.mp4'
& $Ffmpeg -y -hide_banner -loglevel error -framerate 15 -i $frames -c:v libx264 -crf 24 -preset medium -pix_fmt yuv420p -movflags +faststart -an $output
if($LASTEXITCODE -ne 0){throw 'Crash preview encoding failed'}
Get-Item $output | Select-Object Name,Length
