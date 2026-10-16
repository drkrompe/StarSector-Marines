param(
    [string]$Ffmpeg = 'ffmpeg',
    [string]$StarsectorCore = 'C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core',
    [ValidateSet('missile-aa-effects', 'missile-aa-fighters')]
    [string]$Scene = 'missile-aa-effects',
    [string]$OutputDir = ''
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
if (!$OutputDir) { $OutputDir = $PSScriptRoot }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$frames = Join-Path $repo "build/snapshots/$Scene/frames/frame-%04d.png"
$timelinePath = Join-Path $repo "build/reports/scenes/$Scene/timeline.json"
$timeline = Get-Content -LiteralPath $timelinePath -Raw | ConvertFrom-Json
$culture = [Globalization.CultureInfo]::InvariantCulture
$clips = @(
    @{ id='swarmer_fire'; path='sounds/sfx_wpn_missiles/pilum_lrm_fire_01.ogg'; pitch=1.0; volume=0.6 },
    @{ id='proximity_charge_launcher_fire'; path='sounds/sfx_wpn_missiles/proximity_charge_launcher_fire_01.ogg'; pitch=1.0; volume=0.8 },
    @{ id='marines_missile_impact'; path='sounds/sfx_impacts/explosion_missile_01.ogg'; pitch=1.0; volume=0.55 },
    @{ id='marines_missile_impact'; path='sounds/sfx_impacts/explosion_missile_02.ogg'; pitch=1.0; volume=0.55 },
    @{ id='prox_charge_explosion'; path='sounds/sfx_systems/mine_strike_explosion_01.ogg'; pitch=1.2; volume=0.275 }
)
$duration = ([Math]::Round($timeline.durationSeconds, 3)).ToString($culture)
$ffArgs = @('-hide_banner','-loglevel','warning','-y','-framerate',"$($timeline.fps)",'-i',$frames)
foreach ($clip in $clips) {
    $path = Join-Path $StarsectorCore $clip.path
    if (!(Test-Path -LiteralPath $path)) { throw "Missing reference sound: $path" }
    $ffArgs += @('-i',$path)
}
$events = @($timeline.events | Where-Object { $_.soundId -and $_.seconds -lt $timeline.durationSeconds })
$assignments = @()
$impactIndex = 0
foreach ($event in $events) {
    $clipIndex = switch ($event.soundId) {
        'swarmer_fire' { 0 }
        'proximity_charge_launcher_fire' { 1 }
        'marines_missile_impact' { 2 + (($impactIndex++) % 2) }
        'prox_charge_explosion' { 4 }
        default { throw "Unmapped simulation sound: $($event.soundId)" }
    }
    $assignments += @{ clip=$clipIndex; event=$event }
}
$filters = [Collections.Generic.List[string]]::new()
$mixLabels = [Collections.Generic.List[string]]::new()
for ($clipIndex=0; $clipIndex -lt $clips.Count; $clipIndex++) {
    $matching = @($assignments | Where-Object { $_.clip -eq $clipIndex })
    if ($matching.Count -eq 0) { continue }
    $labels = (0..($matching.Count-1) | ForEach-Object { "[clip${clipIndex}_$_]" }) -join ''
    $rate = [int](48000 * $clips[$clipIndex].pitch)
    $filters.Add("[$($clipIndex+1):a]aresample=48000,asetrate=$rate,aresample=48000,aformat=channel_layouts=mono,asplit=$($matching.Count)$labels")
    for ($i=0; $i -lt $matching.Count; $i++) {
        $event = $matching[$i].event
        $delay = [int][Math]::Round($event.seconds * 1000)
        # Reference mix: nominal source pitch, fixed attenuation, columns placed in stereo.
        # In-game positional attenuation and random pitch/clip variation remain host-owned.
        $gain = ($clips[$clipIndex].volume * 0.45).ToString($culture)
        $column = if ($event.weapon) { $event.weapon } else { $event.lane }
        $pan = switch ($column) {
            'locust' { 'stereo|c0=0.9*c0|c1=0.25*c0' }
            'swarmer' { 'stereo|c0=0.65*c0|c1=0.65*c0' }
            'proximity' { 'stereo|c0=0.25*c0|c1=0.9*c0' }
            default { throw "Unknown preview column: $column" }
        }
        $out = "cue${clipIndex}_$i"
        $filters.Add("[clip${clipIndex}_$i]volume=$gain,pan=$pan,adelay=${delay}|${delay}[$out]")
        $mixLabels.Add("[$out]")
    }
}
$filters.Add("$($mixLabels -join '')amix=inputs=$($mixLabels.Count):normalize=0:dropout_transition=0,alimiter=limit=0.85:level=false:latency=true,apad,atrim=duration=$duration[audio]")
$filterPath = Join-Path $repo "build/snapshots/$Scene/audio-filter.txt"
[IO.File]::WriteAllText($filterPath, ($filters -join ";`n"), [Text.UTF8Encoding]::new($false))
$output = Join-Path $OutputDir 'review.mp4'
$ffArgs += @('-filter_complex_script',$filterPath,'-map','0:v','-map','[audio]','-t',$duration,
    '-c:v','libx264','-crf','18','-pix_fmt','yuv420p','-c:a','aac','-b:a','192k','-movflags','+faststart',$output)
& $Ffmpeg @ffArgs
if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed: $LASTEXITCODE" }
Copy-Item -LiteralPath $timelinePath -Destination (Join-Path $OutputDir 'timeline.json')
Copy-Item -LiteralPath (Join-Path $repo "build/snapshots/$Scene/frames/frame-0024.png") -Destination (Join-Path $OutputDir 'review.png')
Write-Output "Encoded $($events.Count) simulation cues into $output"
