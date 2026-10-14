param([Parameter(Mandatory = $true)][string]$BackupPath)
$ErrorActionPreference = 'Stop'
try {
    if ([System.IO.Directory]::Exists($BackupPath)) {
        $acl = [System.IO.Directory]::GetAccessControl($BackupPath)
    } else {
        $acl = [System.IO.File]::GetAccessControl($BackupPath)
    }
    $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $allowed = @($currentSid, 'S-1-5-18', 'S-1-5-32-544')
    if ($acl.GetOwner([System.Security.Principal.SecurityIdentifier]).Value -notin $allowed) { exit 9 }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -eq 'Allow' -and
            $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -notin $allowed) {
            exit 9
        }
    }
    exit 0
} catch {
    # Do not print paths, ACL identities or original contents on failure.
    exit 10
}
