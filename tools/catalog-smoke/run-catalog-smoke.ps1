param(
    [Parameter(Mandatory = $true)] [string] $ProjectDir,
    [Parameter(Mandatory = $true)] [string] $StarsectorDir,
    [Parameter(Mandatory = $true)] [ValidateSet('additive', 'collision')] [string] $Mode
)

$ErrorActionPreference = 'Stop'

$smokeRoot = Join-Path $ProjectDir "build/catalog-smoke/$Mode"
$modsDir = Join-Path $smokeRoot 'mods'
$savesDir = Join-Path $smokeRoot 'saves'
$screenshotsDir = Join-Path $smokeRoot 'screenshots'
$logsDir = Join-Path $smokeRoot 'logs'
$coreDir = Join-Path $StarsectorDir 'starsector-core'
$sourceBatch = Join-Path $coreDir 'starsector_minimal.bat'
$launcherBatch = Join-Path $smokeRoot 'starsector-catalog-smoke.bat'
$gameLog = Join-Path $logsDir 'starsector.log'
$evidenceLog = Join-Path $smokeRoot 'evidence.log'
$summaryPath = Join-Path $smokeRoot 'summary.json'

foreach ($path in @($savesDir, $screenshotsDir, $logsDir)) {
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}
foreach ($path in @($gameLog, $evidenceLog, $summaryPath)) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force
    }
}

if (!(Test-Path -LiteralPath (Join-Path $modsDir 'enabled_mods.json'))) {
    throw "Catalog smoke fixtures are not staged at $modsDir"
}

function QuotedProperty([string] $name, [string] $value) {
    return '"-D' + $name + '=' + $value + '"'
}

$batch = Get-Content -Raw -LiteralPath $sourceBatch
$batch = $batch.Replace('-Dcom.fs.starfarer.settings.paths.saves=../saves',
    (QuotedProperty 'com.fs.starfarer.settings.paths.saves' $savesDir))
$batch = $batch.Replace('-Dcom.fs.starfarer.settings.paths.screenshots=../screenshots',
    (QuotedProperty 'com.fs.starfarer.settings.paths.screenshots' $screenshotsDir))
$batch = $batch.Replace('-Dcom.fs.starfarer.settings.paths.mods=../mods',
    (QuotedProperty 'com.fs.starfarer.settings.paths.mods' $modsDir))
$batch = $batch.Replace('-Dcom.fs.starfarer.settings.paths.logs=.',
    (QuotedProperty 'com.fs.starfarer.settings.paths.logs' $logsDir))
$batch = $batch.Replace('-classpath ',
    '-DlaunchDirect=true -DstartRes=1024x768 -DstartFS=false -DstartSound=false -classpath ')
[System.IO.File]::WriteAllText($launcherBatch, $batch)

Write-Warning 'This live acceptance task launches the installed Starsector game executable.'
Write-Host "Catalog smoke mode: $Mode"
Write-Host 'The isolated Starsector launcher is using its built-in quick-launch properties; the task will capture application-load evidence and close only the process tree it launched.'
Write-Host "The game installation remains read-only. Smoke state: $smokeRoot"

$process = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList @('/d', '/c', ('"' + $launcherBatch + '"')) `
    -WorkingDirectory $coreDir -PassThru

$deadline = [DateTime]::UtcNow.AddSeconds(90)
$matched = $false
$logText = ''
$observedOrder = @()
try {
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $gameLog) {
            $logText = Get-Content -Raw -LiteralPath $gameLog
            $observedOrder = [regex]::Matches($logText,
                '\[id: (starsector_marines|catalog_smoke_alpha|catalog_smoke_beta)\]') `
                | ForEach-Object { $_.Groups[1].Value }
            $expectedMods = @(
                'starsector_marines',
                'catalog_smoke_alpha',
                'catalog_smoke_beta'
            )
            $hasOrder = $logText.Contains(
                    'Running with the following mods (in order of priority):') `
                -and $observedOrder.Count -eq $expectedMods.Count `
                -and !(Compare-Object `
                    ($observedOrder | Sort-Object) `
                    ($expectedMods | Sort-Object))
            if ($Mode -eq 'additive') {
                $matched = $hasOrder -and $logText.Contains(
                    'TileRegistry: loaded 29 sliced tiles from 9 contributed tilesets')
            } else {
                $matched = $hasOrder `
                    -and $logText.Contains('catalog-smoke.alpha-ground') `
                    -and $logText.Contains('duplicate id') `
                    -and $logText.Contains("mod 'catalog_smoke_alpha'") `
                    -and $logText.Contains("mod 'catalog_smoke_beta'")
            }
            if ($matched) { break }
        }
        if ($process.HasExited) { break }
        Start-Sleep -Milliseconds 500
        $process.Refresh()
    }
} finally {
    if (!$process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>&1 | Out-Null
        $process.WaitForExit(10000) | Out-Null
    }
}

if (Test-Path -LiteralPath $gameLog) {
    $evidence = Get-Content -LiteralPath $gameLog | Where-Object {
        $_ -match 'Running with the following mods' `
            -or $_ -match '\[id: (starsector_marines|catalog_smoke_alpha|catalog_smoke_beta)\]' `
            -or $_ -match 'TileRegistry:' `
            -or $_ -match 'Failed to ingest tileset catalog' `
            -or $_ -match 'duplicate id' `
            -or $_ -match 'catalog-smoke\.alpha-ground'
    }
    $evidence | Set-Content -LiteralPath $evidenceLog
}

$summary = [ordered]@{
    mode = $Mode
    passed = $matched
    gameVersion = '0.98a-RC8'
    configuredEnabledMods = @('starsector_marines', 'catalog_smoke_alpha', 'catalog_smoke_beta')
    observedPriorityOrder = $observedOrder
    modsPath = $modsDir
    gameLog = $gameLog
    evidenceLog = $evidenceLog
}
$summary | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $summaryPath

if (!$matched) {
    $tail = if (Test-Path -LiteralPath $gameLog) {
        (Get-Content -LiteralPath $gameLog -Tail 40) -join [Environment]::NewLine
    } else {
        '<no Starsector log was created>'
    }
    throw "Catalog smoke mode '$Mode' did not reach its expected application-load evidence.`n$tail"
}

Write-Host "Catalog smoke '$Mode' passed. Evidence: $evidenceLog"
