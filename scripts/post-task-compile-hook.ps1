param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [int]$MaxFixRounds = 6,
    [switch]$BackendOnly,
    [switch]$WebOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

if ($BackendOnly -and $WebOnly) {
    throw "BackendOnly and WebOnly cannot both be set."
}

$hookRoot = Join-Path $RepoRoot ".codex-hooks"
$logDir = Join-Path $hookRoot "logs"
$latestLog = Join-Path $logDir "latest-build.log"
$codexCmd = Join-Path $env:APPDATA "npm\codex.cmd"

New-Item -ItemType Directory -Path $hookRoot -Force | Out-Null
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

if (-not (Test-Path $codexCmd)) {
    throw "Cannot find codex CLI at: $codexCmd"
}

function Invoke-LoggedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    Write-Host ""
    Write-Host "==> $Title"
    $prevErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Action 2>&1 | Tee-Object -FilePath $latestLog -Append
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $prevErrorActionPreference
    }

    return ($exitCode -eq 0)
}

function Invoke-ProjectBuild {
    if (Test-Path $latestLog) {
        Remove-Item -LiteralPath $latestLog -Force
    }

    $ranSomething = $false

    if (-not $WebOnly -and (Test-Path (Join-Path $RepoRoot "backend\pom.xml"))) {
        $ranSomething = $true
        Push-Location (Join-Path $RepoRoot "backend")
        try {
            if (-not (Invoke-LoggedCommand -Title "Compiling backend (mvn -DskipTests package)" -Action { cmd /c "mvn -DskipTests package" })) {
                return $false
            }
        }
        finally {
            Pop-Location
        }
    }

    if (-not $BackendOnly -and (Test-Path (Join-Path $RepoRoot "web\package.json"))) {
        $ranSomething = $true
        Push-Location (Join-Path $RepoRoot "web")
        try {
            if (-not (Test-Path "node_modules")) {
                if (-not (Invoke-LoggedCommand -Title "Installing frontend dependencies (npm install)" -Action { cmd /c "npm install" })) {
                    return $false
                }
            }
            if (-not (Invoke-LoggedCommand -Title "Compiling frontend (npm run build)" -Action { cmd /c "npm run build" })) {
                return $false
            }
        }
        finally {
            Pop-Location
        }
    }

    if (-not $ranSomething) {
        "No supported build config found. Checked: backend/pom.xml and web/package.json." | Set-Content -Path $latestLog -Encoding UTF8
        throw "No supported build configuration found in $RepoRoot."
    }

    return $true
}

function Invoke-CodexFix {
    param([Parameter(Mandatory = $true)][int]$Round)

    $prompt = @"
You are fixing compile failures in the current repository.

Requirements:
1. Read and analyze the build log: $latestLog
2. Make only minimal changes required to fix compile errors
3. Do not change unrelated behavior
4. End after the fixes are complete
"@

    Write-Host ""
    Write-Host "==> Auto-fix round ${Round}: asking Codex to fix compile errors"
    $prompt | & $codexCmd exec -C $RepoRoot --dangerously-bypass-approvals-and-sandbox -
    if ($LASTEXITCODE -ne 0) {
        throw "codex exec failed in auto-fix round $Round."
    }
}

function Invoke-SuccessBeep {
    try {
        [Console]::Beep(1046, 180)
        Start-Sleep -Milliseconds 80
        [Console]::Beep(1318, 180)
        Start-Sleep -Milliseconds 80
        [Console]::Beep(1568, 260)
    }
    catch {
        Write-Host "`a"
    }
}

function Invoke-SuccessNotification {
    param(
        [string]$Title = "Codex Hook",
        [string]$Body = "Build passed."
    )

    try {
        [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
        [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

        $safeTitle = [System.Security.SecurityElement]::Escape($Title)
        $safeBody = [System.Security.SecurityElement]::Escape($Body)
        $xml = @"
<toast>
  <visual>
    <binding template='ToastGeneric'>
      <text>$safeTitle</text>
      <text>$safeBody</text>
    </binding>
  </visual>
</toast>
"@

        $doc = New-Object Windows.Data.Xml.Dom.XmlDocument
        $doc.LoadXml($xml)
        $toast = [Windows.UI.Notifications.ToastNotification]::new($doc)
        $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("Windows PowerShell")
        $notifier.Show($toast)
        return
    }
    catch {
    }

    try {
        $wshell = New-Object -ComObject WScript.Shell
        [void]$wshell.Popup($Body, 5, $Title, 64)
    }
    catch {
    }
}

Write-Host "RepoRoot: $RepoRoot"
Write-Host "Log file: $latestLog"

for ($round = 0; $round -le $MaxFixRounds; $round++) {
    Write-Host ""
    Write-Host "========================================"
    Write-Host "Build verification round: $($round + 1)"
    Write-Host "========================================"

    if (Invoke-ProjectBuild) {
        Write-Host ""
        Write-Host "Build passed."
        Invoke-SuccessBeep
        Invoke-SuccessNotification -Title "Codex Build Hook" -Body "Build passed for $RepoRoot"
        exit 0
    }

    if ($round -ge $MaxFixRounds) {
        Write-Host ""
        Write-Host "Build still failing after $MaxFixRounds auto-fix rounds."
        Write-Host "See log: $latestLog"
        exit 1
    }

    Invoke-CodexFix -Round ($round + 1)
}

exit 1
