param(
    [string]$SshTarget = 'baota-124',
    [string]$DeployRoot = '/www/wwwroot/wealth-hub',
    [string]$HealthUrl = 'https://www.timelordtty.cn/wealth-hub/',
    [switch]$SkipBuild,
    [string]$ReadinessEvidence,
    [string]$Jar,
    [string]$Pc,
    [string]$H5,
    [string]$BackupParent,
    [string]$BackupReceipt
)
$ErrorActionPreference = 'Stop'
if ($SshTarget -ne 'baota-124' -or $DeployRoot -ne '/www/wwwroot/wealth-hub' -or
    $HealthUrl -ne 'https://www.timelordtty.cn/wealth-hub/') {
    throw 'BLOCKED: target contract is fixed.'
}
# This entry never builds implicitly: Work must supply verified artifacts and
# complete readiness evidence. The adapter independently checks every gate.
if (-not $ReadinessEvidence -or -not $Jar -or -not $Pc -or -not $H5 -or
    -not $BackupParent -or -not $BackupReceipt -or $BackupReceipt -eq 'OFFLINE_AUTO_CONFIRM') {
    throw 'BLOCKED: live streaming adapter requires complete readiness evidence, artifacts, restricted local originals and an independently verified backup receipt. No network or service action was performed.'
}
& python (Join-Path $PSScriptRoot 'ssh_stream_adapter.py') --readiness $ReadinessEvidence --jar $Jar --pc $Pc --h5 $H5 --backup-parent $BackupParent --backup-receipt $BackupReceipt
if ($LASTEXITCODE -ne 0) { throw "BLOCKED: streaming transaction exited $LASTEXITCODE; inspect its recovery status." }
