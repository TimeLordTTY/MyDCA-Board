param(
    [string]$SshTarget = 'baota-124',
    [string]$DeployRoot = '/www/wwwroot/wealth-hub',
    [string]$HealthUrl = 'https://www.timelordtty.cn/wealth-hub/',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
if ($SshTarget -ne 'baota-124' -or $DeployRoot -ne '/www/wwwroot/wealth-hub' -or
    $HealthUrl -ne 'https://www.timelordtty.cn/wealth-hub/') {
    throw 'BLOCKED: target contract is fixed.'
}
# Fail closed before build/upload/service action. The offline transaction lives
# in systemd_local_rollback.py; it cannot be used as evidence of live recovery.
throw 'BLOCKED: live streaming adapter, configuration inventory, upstream route and restricted D:/AicoreRescue ACL are not verified. No network or service action was performed.'
