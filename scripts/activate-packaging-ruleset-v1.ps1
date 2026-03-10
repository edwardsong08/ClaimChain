# This script creates a new draft PACKAGING ruleset, activates it, and then you should run preview/create package separately.
$ErrorActionPreference = "Stop"

$BASE_URL = "http://18.222.221.138"
$TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9BRE1JTiIsInN1YiI6ImVkd2FyZHNvbmcwOCthZG1pbkBnbWFpbC5jb20iLCJpYXQiOjE3NzMxMjE0MDAsImV4cCI6MTc3MzIwNzgwMH0.JEWB10V5vtHCtHSLmrhMBUcusa5_ujli53p6W0lRR3M"

function Escape-JsonString {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $escaped = $Value `
        -replace '\\', '\\\\' `
        -replace '"', '\"' `
        -replace "`r", '\r' `
        -replace "`n", '\n' `
        -replace "`t", '\t' `
        -replace "`b", '\b' `
        -replace "`f", '\f'

    return $escaped
}

if ([string]::IsNullOrWhiteSpace($TOKEN) -or $TOKEN -eq "PASTE_FRESH_ADMIN_ACCESS_TOKEN") {
    throw "Set `$TOKEN to a fresh admin access token before running."
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ConfigPath = Join-Path $RepoRoot "docs/rulesets/packaging-ruleset-v1-2026-03-10.json"

if (-not (Test-Path $ConfigPath)) {
    throw "Ruleset JSON not found at: $ConfigPath"
}

$authHeaders = @{
    Authorization = "Bearer $TOKEN"
}

Write-Host "Step 1/5: Fetching current PACKAGING rulesets list ..."
$currentRulesets = Invoke-RestMethod `
    -Method Get `
    -Uri "$BASE_URL/api/admin/rulesets/PACKAGING" `
    -Headers $authHeaders `
    -TimeoutSec 60

Write-Host "Current PACKAGING rulesets:"
$currentRulesets | ConvertTo-Json -Depth 30

Write-Host "Step 2/5: Reading packaging ruleset config from $ConfigPath ..."
$configJson = Get-Content -Path $ConfigPath -Raw
$escapedConfigJson = Escape-JsonString -Value $configJson
$draftBody = "{`"configJson`":`"$escapedConfigJson`"}"

Write-Host "Step 3/5: Creating PACKAGING draft ruleset ..."
$draftResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$BASE_URL/api/admin/rulesets/PACKAGING/draft" `
    -Headers $authHeaders `
    -ContentType "application/json" `
    -Body $draftBody `
    -TimeoutSec 60

$rulesetId = $draftResponse.id
if (-not $rulesetId) {
    throw "Draft creation succeeded but no ruleset id was returned."
}

Write-Host "Draft created. rulesetId=$rulesetId"
$confirmation = Read-Host "Activate ruleset id $rulesetId now? Type YES to continue"
if ($confirmation -ne "YES") {
    Write-Host "Activation canceled. Draft was created but not activated."
    return
}

Write-Host "Step 4/5: Activating PACKAGING ruleset id $rulesetId ..."
$activateResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$BASE_URL/api/admin/rulesets/$rulesetId/activate" `
    -Headers $authHeaders `
    -TimeoutSec 60

Write-Host "Activation complete. status=$($activateResponse.status) version=$($activateResponse.version)"

Write-Host "Step 5/5: Fetching active PACKAGING ruleset ..."
$activeResponse = Invoke-RestMethod `
    -Method Get `
    -Uri "$BASE_URL/api/admin/rulesets/PACKAGING/active" `
    -Headers $authHeaders `
    -TimeoutSec 60

Write-Host "Active PACKAGING ruleset:"
$activeResponse | ConvertTo-Json -Depth 30
