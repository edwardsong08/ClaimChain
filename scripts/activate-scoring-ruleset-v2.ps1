# This script creates a new draft SCORING ruleset, activates it, and then you should rescore claims separately.
$ErrorActionPreference = "Stop"

$BASE_URL = "http://18.222.221.138"
$TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9BRE1JTiIsInN1YiI6ImVkd2FyZHNvbmcwOCthZG1pbkBnbWFpbC5jb20iLCJpYXQiOjE3NzMxMjE0MDAsImV4cCI6MTc3MzIwNzgwMH0.JEWB10V5vtHCtHSLmrhMBUcusa5_ujli53p6W0lRR3M"

if ([string]::IsNullOrWhiteSpace($TOKEN) -or $TOKEN -eq "PASTE_FRESH_ADMIN_ACCESS_TOKEN") {
    throw "Set `$TOKEN to a fresh admin access token before running."
}

function Escape-JsonString {
    param([string]$Value)

    if ($null -eq $Value) { return "" }

    $escaped = $Value.Replace('\', '\\')
    $escaped = $escaped.Replace('"', '\"')
    $escaped = $escaped.Replace("`r", '\r')
    $escaped = $escaped.Replace("`n", '\n')
    $escaped = $escaped.Replace("`t", '\t')
    return $escaped
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ConfigPath = Join-Path $RepoRoot "docs/rulesets/scoring-ruleset-v2-2026-03-10.json"

if (-not (Test-Path $ConfigPath)) {
    throw "Ruleset JSON not found at: $ConfigPath"
}

$headers = @{
    Authorization = "Bearer $TOKEN"
}

Write-Host "Step 1/5: Reading scoring ruleset config from $ConfigPath ..."
$configJson = Get-Content -Path $ConfigPath -Raw

Write-Host "Pre-check: Fetching current active SCORING ruleset ..."
$currentActive = Invoke-RestMethod `
    -Method Get `
    -Uri "$BASE_URL/api/admin/rulesets/SCORING/active" `
    -Headers $headers `
    -TimeoutSec 60

Write-Host "Current active ruleset id=$($currentActive.id) version=$($currentActive.version)"

Write-Host "Preparing draft request body ..."
$escapedConfigJson = Escape-JsonString $configJson
$draftBody = "{""configJson"":""$escapedConfigJson""}"

Write-Host "Draft request body prepared. Length=$($draftBody.Length)"
Write-Host "Step 2/5: Creating SCORING draft ruleset ..."

$draftResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$BASE_URL/api/admin/rulesets/SCORING/draft" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $draftBody `
    -TimeoutSec 60

$rulesetId = $draftResponse.id
if (-not $rulesetId) {
    throw "Draft creation succeeded but no ruleset id was returned."
}

Write-Host "Draft created. rulesetId=$rulesetId version=$($draftResponse.version)"

$confirm = Read-Host "Activate ruleset id $rulesetId now? Type YES to continue"
if ($confirm -ne "YES") {
    throw "Activation cancelled by user."
}

Write-Host "Step 3/5: Activating ruleset id $rulesetId ..."
$activateResponse = Invoke-RestMethod `
    -Method Post `
    -Uri "$BASE_URL/api/admin/rulesets/$rulesetId/activate" `
    -Headers $headers `
    -TimeoutSec 60

Write-Host "Activation complete. status=$($activateResponse.status) version=$($activateResponse.version)"

Write-Host "Step 4/5: Fetching active SCORING ruleset ..."
$activeResponse = Invoke-RestMethod `
    -Method Get `
    -Uri "$BASE_URL/api/admin/rulesets/SCORING/active" `
    -Headers $headers `
    -TimeoutSec 60

Write-Host "Active SCORING ruleset:"
$activeResponse | ConvertTo-Json -Depth 30

Write-Host "Step 5/5: Done. Next step is to rescore affected claims separately."