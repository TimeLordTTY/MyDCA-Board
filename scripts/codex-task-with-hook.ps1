param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$TaskPrompt,
    [int]$MaxFixRounds = 6,
    [switch]$BackendOnly,
    [switch]$WebOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$codexCmd = Join-Path $env:APPDATA "npm\codex.cmd"
$hookScript = Join-Path $PSScriptRoot "post-task-compile-hook.ps1"

if (-not (Test-Path $codexCmd)) {
    throw "Cannot find codex CLI at: $codexCmd"
}

if (-not (Test-Path $hookScript)) {
    throw "Cannot find hook script: $hookScript"
}

Write-Host "==> Running Codex task"
& $codexCmd exec -C $repoRoot --dangerously-bypass-approvals-and-sandbox $TaskPrompt
if ($LASTEXITCODE -ne 0) {
    throw "Codex task failed, aborting hook execution."
}

Write-Host ""
Write-Host "==> Running post-task compile hook"
$hookParams = @{
    RepoRoot = $repoRoot
    MaxFixRounds = $MaxFixRounds
}
if ($BackendOnly) { $hookParams.BackendOnly = $true }
if ($WebOnly) { $hookParams.WebOnly = $true }

& $hookScript @hookParams
exit $LASTEXITCODE
