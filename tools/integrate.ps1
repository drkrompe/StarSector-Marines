<#
.SYNOPSIS
  CLAUDE.md's integration step for one session branch, with a hard stop on any failure.

.DESCRIPTION
  From the main workspace: require `main` checked out and clean, fetch origin,
  fast-forward local main to origin/main, fast-forward-merge the session branch,
  push main to origin, and only then push main to hub (skipped with a message
  when the Mac is unreachable). Stops with a non-zero exit at the first failure.
  Never force-pushes and never pushes anything but `main`.

  Run it from anywhere inside the repo or one of its worktrees; it locates the
  main workspace itself. -Repo overrides that (used by the failure-path check).

.EXAMPLE
  powershell -File tools/integrate.ps1 session/my-change
#>
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Branch,
    [string]$Repo
)
$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    Write-Host "integrate: $Message" -ForegroundColor Red
    exit 1
}

# Run a git command; stop the whole script unless it exits 0. Output is shown, never piped.
function Invoke-Git {
    Write-Host "> git $($args -join ' ')"
    & git @args
    if ($LASTEXITCODE -ne 0) { Fail "git $($args -join ' ') failed (exit $LASTEXITCODE)" }
}

# Run a git command and return its trimmed stdout, stopping on failure.
function Get-GitOut {
    $out = & git @args
    if ($LASTEXITCODE -ne 0) { Fail "git $($args -join ' ') failed (exit $LASTEXITCODE)" }
    return ($out -join "`n").Trim()
}

if (-not $Repo) {
    $common = & git rev-parse --path-format=absolute --git-common-dir
    if ($LASTEXITCODE -ne 0) { Fail 'not inside a git repository' }
    $Repo = Split-Path -Parent ($common -join '')
}
Set-Location -LiteralPath $Repo

if ($Branch -eq 'main') { Fail 'the branch to integrate must not be main' }
Invoke-Git rev-parse --verify --quiet "refs/heads/$Branch^{commit}"

$current = Get-GitOut rev-parse --abbrev-ref HEAD
if ($current -ne 'main') { Fail "main workspace is on '$current', not main" }
$dirty = Get-GitOut status --porcelain
if ($dirty) { Fail "main workspace is not clean:`n$dirty" }

Invoke-Git fetch origin
Invoke-Git merge --ff-only origin/main
Invoke-Git merge --ff-only $Branch

Invoke-Git push origin main

$ErrorActionPreference = 'Continue'
& git ls-remote hub 2>$null | Out-Null
$hubStatus = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($hubStatus -eq 0) {
    Invoke-Git push hub main
} else {
    Write-Host 'integrate: hub (Mac) unreachable, skipping the hub push.' -ForegroundColor Yellow
}
Write-Host 'integrate: done.' -ForegroundColor Green
