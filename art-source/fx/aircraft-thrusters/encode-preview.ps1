param([string]$Ffmpeg = 'ffmpeg')
$ErrorActionPreference = 'Stop'
$repository = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$frames = Join-Path $repository 'build/snapshots/aircraft-thrusters/frames/frame-%04d.png'
& $Ffmpeg -y -hide_banner -loglevel error -framerate 15 -i $frames -c:v libx264 -crf 20 -preset medium -pix_fmt yuv420p -movflags +faststart -an (Join-Path $PSScriptRoot 'flight.mp4')
if ($LASTEXITCODE -ne 0) { throw 'MP4 encoding failed' }
Get-Item (Join-Path $PSScriptRoot 'flight.mp4') | Select-Object Name,Length
