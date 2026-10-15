param(
 [string]$Ffmpeg = 'ffmpeg',
 [ValidateSet('flight','night','both')][string]$Preview='flight'
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$jobs=@()
if($Preview -in @('flight','both')){$jobs+=@{suite='aircraft-thrusters';output='flight.mp4'}}
if($Preview -in @('night','both')){$jobs+=@{suite='aircraft-thrusters-night';output='night-flight.mp4'}}
foreach($job in $jobs){
 $frameRoot=Join-Path $repository ("build/snapshots/"+$job.suite+'/frames')
 if(-not(Test-Path (Join-Path $frameRoot 'frame-0000.png'))){throw "Missing frames for $($job.suite); run its opt-in createSnapshots suite first"}
 $frames=Join-Path $frameRoot 'frame-%04d.png'
 $output=Join-Path $PSScriptRoot $job.output
 & $Ffmpeg -y -hide_banner -loglevel error -framerate 15 -i $frames -c:v libx264 -crf 20 -preset medium -pix_fmt yuv420p -movflags +faststart -an $output
 if ($LASTEXITCODE -ne 0) { throw "MP4 encoding failed for $($job.suite)" }
 Get-Item $output | Select-Object Name,Length
}
