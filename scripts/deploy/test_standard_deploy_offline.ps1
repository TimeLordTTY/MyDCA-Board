# No native network/service commands can run: every entry point is a deny stub.
$ErrorActionPreference = 'Stop'
function ssh { throw 'NETWORK_FORBIDDEN: ssh' }
function scp { throw 'NETWORK_FORBIDDEN: scp' }
function curl { throw 'NETWORK_FORBIDDEN: curl' }
function systemctl { throw 'SERVICE_FORBIDDEN: systemctl' }
function mvn { throw 'BUILD_UNEXPECTED' }
function npm { throw 'BUILD_UNEXPECTED' }
$cases = @(
    @{ Args = @{}; Expected = 'BLOCKED: live streaming adapter' },
    @{ Args = @{ SkipBuild = $true }; Expected = 'BLOCKED: live streaming adapter' },
    @{ Args = @{ SshTarget = 'outside.invalid' }; Expected = 'BLOCKED: target contract is fixed.' },
    @{ Args = @{ DeployRoot = '/fixture/outside' }; Expected = 'BLOCKED: target contract is fixed.' },
    @{ Args = @{ HealthUrl = 'https://outside.invalid/' }; Expected = 'BLOCKED: target contract is fixed.' }
)
foreach ($case in $cases) {
    $blocked = $false
    try {
        $arguments = $case.Args
        & (Join-Path $PSScriptRoot 'standard_deploy.ps1') @arguments
    } catch {
        if (-not $_.Exception.Message.StartsWith($case.Expected)) { throw }
        $blocked = $true
    }
    if (-not $blocked) { throw 'Unsafe entry point was not blocked.' }
}
Write-Output 'offline_powershell_tests=PASS (5 fixed-target/live-gate cases; no network, service or build action)'
