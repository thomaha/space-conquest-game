$ErrorActionPreference='Stop'
$p=Join-Path $pwd 'engine/src/main/resources/technologies.json'
$s=[IO.File]::ReadAllText($p)
function Edit-App([string]$id, [scriptblock]$edit) {
 $m=[regex]::Match($script:s,'"id"\s*:\s*"'+[regex]::Escape($id)+'"')
 if (!$m.Success) { throw "Missing $id" }
 $start=$script:s.LastIndexOf('{',$m.Index); $depth=0; $quoted=$false; $escape=$false
 for($i=$start;$i -lt $script:s.Length;$i++) {
  $c=$script:s[$i]
  if($escape){$escape=$false;continue}
  if($quoted -and $c -eq '\'){$escape=$true;continue}
  if($c -eq '"'){$quoted=!$quoted;continue}
  if(!$quoted){if($c -eq '{'){$depth++};if($c -eq '}'){$depth--;if($depth -eq 0){break}}}
 }
 $obj=$script:s.Substring($start,$i-$start+1)|ConvertFrom-Json
 & $edit $obj
 $block=$obj|ConvertTo-Json -Depth 80
 $script:s=$script:s.Substring(0,$start)+$block+$script:s.Substring($i+1)
}
function Service($type,$mode,$capacity,$workers,$profession,$dry,$drive,$power,$perKg,$fee,$markup,$stock,$materials,$work) {
 [ordered]@{facilityType=$type;professionId=$profession;workers=$workers;mode=$mode;passengers=($mode -ne 'MASS_DRIVER');shipLaunch=($mode -ne 'MASS_DRIVER');capacityKgPerTier=$capacity;powerKwPerTier=$power;powerKwPerKg=$perKg;feePerKg=$fee;minimumFee=0.01;powerPriceMultiplier=$markup;driveId=$drive;vehicleDryMassKg=$dry;fuelStockKgPerTier=$stock;infrastructure=[ordered]@{constructionWorkPerTier=$work;expansionWorkPerTier=200;investmentCredits=8000;constructionWorkPerDay=100;constructionMaterialsKg=$materials;tierCapacityMultiplier=1.5;expansionCapacityFraction=0.5;complexityOffset=0;complexityPerTier=1;shedDuringBrownout=$false;environment='UNIVERSAL';minimumGravityMs2=0;maximumGravityMs2=100;byproductMaterialIds=@();marketOrientations=@('TRANSPORT')}}
}
$bill=@{refined_iron=500;refined_aluminum=150;refined_copper=50;silicon=10}
$rocket=Service 'cargo_terminal' 'ROCKET' 100000 50 'technician' 1000 'mod_chemical_rocket' 0 0 0 0 1000 $bill 500
# Add terminal as a researchable application under rocketry, preserving other app blocks.
$m=[regex]::Match($s,'"id"\s*:\s*"rocketry"'); if(!$m.Success){throw 'rocketry missing'}
$at=$s.IndexOf('[',$s.IndexOf('"applications"',$m.Index))+1
$app=[ordered]@{id='cargo_terminal';name='Cargo terminal';description='Paid surface launch and cargo handling with application-defined capacity and propulsion.';researchPoints=500;refinementResearchPoints=500;requiredTechnologies=@('industrial_production');affectedFactors=@('launch_transport_cost');costToBuildPerUnit=500;requiredMaterials=@('refined_iron','refined_aluminum','refined_copper','silicon');complexity=3;infrastructureService=$rocket;operatingReserve=@{targetDays=30;refillBelowDays=10;maximumCashCredits=1000000;treasuryReserveCredits=10000;maintenanceCreditsPerTier=80}}
$s=$s.Insert($at,"`r`n"+($app|ConvertTo-Json -Depth 30)+',')
Edit-App 'mass_driver_launch_tracks' {param($a) $a|Add-Member infrastructureService (Service 'mass_driver' 'MASS_DRIVER' 1000 50 'technician' 0 $null 250 0 0.0005 1.5 0 $bill 500)}
Edit-App 'space_elevators' {param($a) $a|Add-Member infrastructureService (Service 'SPACE_ELEVATOR' 'SPACE_ELEVATOR' 100000 0 'technician' 0 $null 0 0.02 0.05 0 0 @{refined_iron=50000;refined_aluminum=20000;refined_copper=5000} 10)}
[IO.File]::WriteAllText($p,$s)
