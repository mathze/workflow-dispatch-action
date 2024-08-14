<#
See CONTRIBUTING.adoc for details
#>
$testNames = @(
    "test_noMarkerWithoutPayload"
    "test_noMarkerWithPayload"
    "test_WithExtId"
    "test_WithWaitTimeout"
    "test_WithFail"
)
$rootDir = "./src/jstest/act"
$secrets = "$rootDir/.secrets"
$failedTest = $testNames.Length
$testNames | ForEach-Object {
    $workflow = "$_.yml"
    Write-Host "🛫 Running test workflow $workflow"
    act workflow_dispatch -W ".github/workflows/$workflow" --secret-file "$secrets" --pull=false
    $resState = ''
    if (0 -eq $LASTEXITCODE) {
        $resState = '✅'
        $failedTest = $failedTest - 1
    } else {
        $resState = '❌'
    }
    $msg = "$resState Workflow test $workflow finished with exitcode $LASTEXITCODE $resState"
    $reps = [math]::ceiling(($msg.Length - 2 ) / 2) - 1
    $border = ($resState * $reps)
    $border = $border.SubString(0, [math]::min($msg.Length, $border.Length))
    Write-Host ""
    Write-Host $border
    Write-Host $msg
    Write-Host $border
    Write-Host ""
}
Write-Host "Summary:"
Write-Host "$failedTest tests failed"
