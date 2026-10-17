$ErrorActionPreference='Stop'
$path=Join-Path $PSScriptRoot '../engine/src/main/resources/technologies.json'
$script:json=[IO.File]::ReadAllText($path)
function End-Token([string]$s,[int]$start,[char]$open,[char]$close) {
    $depth=0; $quoted=$false; $escaped=$false
    for($i=$start;$i -lt $s.Length;$i++) {
        $c=$s[$i]
        if($quoted) { if($escaped){$escaped=$false} elseif($c -eq '\'){$escaped=$true} elseif($c -eq '"'){$quoted=$false}; continue }
        if($c -eq '"'){$quoted=$true;continue}
        if($c -eq $open){$depth++}; if($c -eq $close){$depth--; if($depth -eq 0){return $i}}
    }
    throw 'Unclosed JSON token'
}
function Span([string]$id) {
    $m=[regex]::Match($script:json,'"id"\s*:\s*"'+[regex]::Escape($id)+'"')
    if(-not $m.Success){return $null}
    $start=$script:json.LastIndexOf('{',$m.Index)
    return @($start,(End-Token $script:json $start '{' '}'))
}
function Put-App([string]$tech,$app) {
    $span=Span $app.id; $text=$app|ConvertTo-Json -Depth 100
    if($span) { $script:json=$script:json.Remove($span[0],$span[1]-$span[0]+1).Insert($span[0],$text); return }
    $parent=Span $tech; $m=[regex]::Match($script:json.Substring($parent[0],$parent[1]-$parent[0]+1),'"applications"\s*:\s*\[')
    $start=$parent[0]+$m.Index+$m.Length-1; $end=End-Token $script:json $start '[' ']'
    $comma=if($script:json.Substring($start+1,$end-$start-1).Trim()) {','} else {''}
    $script:json=$script:json.Insert($end,"$comma`n$text`n")
}
function Reserve { return @{targetDays=3; refillBelowDays=1; maximumCashCredits=50000; treasuryReserveCredits=10000; maintenanceCreditsPerTier=80} }
function Work($id,$kind,$workers,$energy) {
    return @{applicationId=$id;processType=$kind;workPerBatch=1000;energyKwhPerBatch=$energy;minimumProcessingDays=1;
        equipment=@{id=$id+'_line';processType=$kind;workUnitsPerDay=1000;referenceOperators=$workers;operatorProfession='industrial_worker';autonomousFraction=0;operatorSkill=1;parallelLines=1}}
}
function Factory($byproducts=@(),$orientations=@('METALLURGY','CONSUMER')) {
    return @{constructionWorkPerTier=500;expansionWorkPerTier=200;investmentCredits=8000;constructionWorkPerDay=100;
        constructionMaterialsKg=@{refined_iron=500;refined_aluminum=150;refined_copper=50;silicon=10};tierCapacityMultiplier=1.5;expansionCapacityFraction=.5;
        complexityOffset=0;complexityPerTier=1;shedDuringBrownout=$true;environment='UNIVERSAL';minimumGravityMs2=0;maximumGravityMs2=100;
        byproductMaterialIds=@($byproducts);marketOrientations=@($orientations)}
}
function App($id,$name,$requirements,$recipe) {
    return [ordered]@{id=$id;name=$name;description='Application-defined production with finite inputs, equipment, work, electricity and funded market delivery. Process scales are provisional.';
        requiredTechnologies=@($requirements);affectedFactors=@('industrial_production');researchPoints=500;refinementResearchPoints=500;
        costToBuildPerUnit=80;requiredMaterials=@('refined_iron','refined_aluminum','refined_copper','silicon');complexity=4;operatingReserve=(Reserve);productionRecipe=$recipe}
}
function Recipe($id,$inputs,$outputs,$workers,$kw,$tier,$requirements,$kind='PROCESSING') {
    $sources=@(); foreach($entry in $inputs.GetEnumerator()) {$sources+=@{source='MARKET';materialId=$entry.Key;kgPerBatch=$entry.Value}}
    return @{applicationId=$id;requiredTechnologies=@($requirements);improvements=@{supply_chain_automation=.1};inputsKg=$inputs;outputsKg=$outputs;
        workersPerBatch=$workers;minTier=$tier;powerDrawKw=$kw;extractsDeposit=$false;work=(Work $id $kind $workers ($kw*24));factory=(Factory);inputSources=@($sources)}
}
function Add-Process($tech,$id,$name,$inputs,$outputs,$workers,$kw,$tier,$requirements) {
    $requirements=@($requirements | Select-Object -Unique)
    $recipe=Recipe $id $inputs $outputs $workers $kw $tier $requirements
    Put-App $tech (App $id $name $requirements $recipe)
}
# Preserve existing legacy batch bills and rated electricity while giving each installation finite equipment.
Add-Process 'rocketry' 'rp1_refining' 'RP-1 refining' @{hydrocarbons=1000} @{rp1_kerosene=850} 10 1500 1 @('rocketry','electricity')
Add-Process 'rocketry' 'oxygen_liquefaction' 'Oxygen liquefaction' @{oxygen_gas=1000} @{liquid_oxygen=1000} 10 3000 1 @('rocketry','electricity')
Add-Process 'methalox_propulsion' 'methane_liquefaction' 'Methane liquefaction' @{methane_ice=1000} @{liquid_methane=1000} 10 3500 1 @('methalox_propulsion','electricity')
Add-Process 'hydrolox_propulsion' 'hydrogen_liquefaction' 'Hydrogen liquefaction' @{hydrogen_gas=1000} @{liquid_hydrogen=1000} 10 6000 1 @('hydrolox_propulsion','electricity')
Add-Process 'nuclear_fusion' 'fuel_fusion_pellets' 'Fusion fuel pellet synthesis' @{deuterium_gas=500;helium_3=500} @{fusion_fuel_pellets=1000} 100 15000 3 @('nuclear_fusion','industrial_production','electricity')
foreach($id in @('consumer_goods_mfg','luxury_goods_mfg','cannon_ammunition_mfg','guided_missile_mfg','mass_driver_ammunition_mfg','kinetic_pd_ammunition_mfg')) {
    $span=Span $id; $a=$script:json.Substring($span[0],$span[1]-$span[0]+1)|ConvertFrom-Json
    if($id -eq 'consumer_goods_mfg') {$r=Recipe $id @{bio_polymers=499;refined_aluminum=250;refined_copper=150;silicon=100;refined_rare_earths=1} @{consumer_goods=1000} 10 6000 1 @('industrial_production')}
    elseif($id -eq 'luxury_goods_mfg') {$r=Recipe $id @{consumer_goods=500;bio_polymers=200;refined_copper=100} @{luxury_goods=800} 10 5000 1 @('industrial_production')}
    else {
        $r=$a.productionRecipe
        if($id -in @('mass_driver_ammunition_mfg','kinetic_pd_ammunition_mfg')) {
            $r.inputsKg|Add-Member NoteProperty 'refined_tungsten' $r.inputsKg.tungsten_ore; $r.inputsKg.PSObject.Properties.Remove('tungsten_ore')
            $a.requiredMaterials=@($a.requiredMaterials|ForEach-Object {if($_ -eq 'tungsten_ore'){'refined_tungsten'}else{$_}})
        }
        $r|Add-Member NoteProperty work (Work $id 'AMMUNITION_ASSEMBLY' $r.workersPerBatch ($r.powerDrawKw*24))
        $r|Add-Member NoteProperty factory (Factory @() @('CONSUMER','METALLURGY'))
    }
    $a|Add-Member NoteProperty productionRecipe $r -Force
    Put-App 'industrial_production' $a
}
Add-Process 'industrial_production' 'synthesis_metamaterials' 'Metamaterial composite synthesis' @{graphene=400;refined_titanium=300;gold=300} @{metamaterial_composites=1000} 100 18000 3 @('industrial_production','nanotechnology')
Add-Process 'antimatter' 'synthesis_antimatter_cells' 'Empty antimatter containment cell manufacturing' @{refined_tungsten=500;superconducting_cuprates=300;inconel_alloy=200} @{antimatter_containment_cell=1000} 100 25000 4 @('antimatter','industrial_production','superconductors')
Add-Process 'computers' 'synthesis_quantum_spin_glass' 'Quantum spin glass fabrication' @{refined_copper=400;refined_manganese=300;refined_iron=300} @{quantum_spin_glass=1000} 100 16000 3 @('computers','industrial_production','superconductors')
Add-Process 'industrial_production' 'synthesis_metallic_hydrogen' 'Metallic hydrogen synthesis' @{hydrogen_gas=1000} @{stabilized_metallic_hydrogen=1000} 100 30000 4 @('industrial_production','nuclear_fusion')
$s=Span 'synthesis_metallic_hydrogen'; $a=$script:json.Substring($s[0],$s[1]-$s[0]+1)|ConvertFrom-Json; $a.productionRecipe.factory.environment='GRAVITY_LOCKED'; $a.productionRecipe.factory.minimumGravityMs2=5; Put-App 'industrial_production' $a
Add-Process 'nanotechnology' 'synthesis_monomolecular_wire' 'Monomolecular wire polymerization' @{carbon_nanotubes=500;graphene=500} @{monomolecular_wire=1000} 100 20000 3 @('nanotechnology','industrial_production')
Add-Process 'industrial_production' 'synthesis_boron_nitride' 'Boron nitride crystallization' @{boron_ore=2500;nitrogen_gas=500} @{crystalline_boron_nitride=1000} 100 14000 2 @('industrial_production','electricity')
$materialPath=Join-Path $PSScriptRoot '../engine/src/main/resources/materials.json'
$script:materials=[IO.File]::ReadAllText($materialPath)|ConvertFrom-Json
$newOre=[ordered]@{id='gallium_bearing_ore';name='Gallium-bearing ore';description='Aggregate gallium-bearing mineral feedstock with a declared trace gallium fraction.';foundInNature=$true;composition=@{Ga=1;Si=39;O=60};weightPerUnit=3;strength=0;complexity=4}
$script:materials+=([pscustomobject]$newOre)
foreach($ore in @('lead_ore','neodymium_ore','magnesium_ore','tungsten_ore','cobalt_ore','tin_ore','zinc_ore','beryllium_ore','boron_ore','gallium_bearing_ore','methane_ice','nitrogen_gas','helium_3')) {
    $id=$ore+'_extraction'; $r=Recipe $id @{} @{$ore=1000} 100 500 1 @('industrial_production','electricity') 'EXTRACTION'
    $r.inputSources=@(@{source='DEPOSIT';materialId=$ore;kgPerBatch=1000}); $r.extractsDeposit=$true
    Put-App 'industrial_production' (App $id (($ore -replace '_',' ')+' extraction') @('industrial_production','electricity') $r)
}
$refiners=@(
    @('lithium_refining','lithium_ore',@{refined_lithium=.9;refined_aluminum=.5;silicon=.4}),
    @('lead_refining','lead_ore',@{refined_lead=.95}),
    @('neodymium_separation','neodymium_ore',@{refined_neodymium=.9;refined_cerium=.9;refined_lanthanum=.9}),
    @('magnesium_refining','magnesium_ore',@{refined_magnesium=.95}),
    @('tungsten_refining','tungsten_ore',@{refined_tungsten=.95;refined_iron=.9;refined_manganese=.9}),
    @('cobalt_refining','cobalt_ore',@{refined_cobalt=.95;refined_arsenic=.9}),
    @('tin_refining','tin_ore',@{refined_tin=.95}), @('zinc_refining','zinc_ore',@{refined_zinc=.95}),
    @('beryllium_refining','beryllium_ore',@{refined_beryllium=.9;refined_aluminum=.5;silicon=.4}),
    @('phosphorus_refining','phosphates',@{refined_phosphorus=.9}),
    @('gallium_refining','gallium_bearing_ore',@{refined_gallium=.9;silicon=.5}),
    @('sodium_separation','nitrates',@{liquid_sodium=.9})
)
foreach($spec in $refiners) {
    $id=$spec[0];$feed=$spec[1];$recoveries=$spec[2];$outputs=@{}
    $ore=$script:materials|Where-Object id -eq $feed
    foreach($entry in $recoveries.GetEnumerator()) {
        $product=$script:materials|Where-Object id -eq $entry.Key
        $element=@($product.composition.PSObject.Properties.Name)[0]
        $outputs[$entry.Key]=1000*$ore.composition.$element/100*$entry.Value
    }
    $r=Recipe $id @{$feed=1000} $outputs 100 4000 1 @('industrial_production','electricity') 'MINERAL_SEPARATION'
    $r.refineryProcess=@{feedstocks=@($feed);defaultFeedstock=$feed;feedstockKgPerBatch=1000;products=@($outputs.Keys);
        profiles=@(@{requiredApplicationId=$id;recoveryFractions=$recoveries;reagentsKg=@{};work=$r.work});tailingsCapacityKg=100000}
    Put-App 'industrial_production' (App $id ($id -replace '_',' ') @('industrial_production','electricity') $r)
}
Add-Process 'industrial_production' 'graphene_fabrication' 'Graphene fabrication' @{carbon=1000} @{graphene=990} 100 8000 2 @('industrial_production','nanotechnology')
Add-Process 'industrial_production' 'nanotube_fabrication' 'Carbon nanotube fabrication' @{carbon=1000} @{carbon_nanotubes=990} 100 8000 2 @('industrial_production','nanotechnology')
Add-Process 'industrial_production' 'silicon_carbide_synthesis' 'Silicon carbide synthesis' @{silicon=700;carbon=300} @{silicon_carbide=1000} 100 5000 2 @('industrial_production','electricity')
Add-Process 'industrial_production' 'aerogel_fabrication' 'Aerogel fabrication' @{silicon=460;oxygen_gas=540} @{aerogel=1000} 100 3000 2 @('industrial_production','electricity')
Add-Process 'industrial_production' 'ceramic_synthesis' 'Industrial ceramic synthesis' @{refined_aluminum=350;silicon=300;oxygen_gas=250;nitrogen_gas=100} @{industrial_ceramics=1000} 100 5000 2 @('industrial_production','electricity')
Add-Process 'nuclear_fission' 'graphite_purification' 'Isotopic graphite purification' @{carbon=1000} @{isotopic_graphite=990} 100 5000 2 @('industrial_production','nuclear_fission')
Add-Process 'industrial_production' 'cryogenic_insulator_fabrication' 'Cryogenic insulator fabrication' @{silicon=600;oxygen_gas=400} @{cryogenic_superinsulator=1000} 100 5000 2 @('industrial_production','hydrolox_propulsion')
Add-Process 'industrial_production' 'radiation_hardened_semiconductor_fabrication' 'Radiation-hardened semiconductor fabrication' @{silicon=500;refined_gallium=500} @{radiation_hardened_semiconductors=1000} 100 8000 2 @('industrial_production','computers')
Add-Process 'nanotechnology' 'plasmonic_alloy_synthesis' 'Plasmonic alloy synthesis' @{gold=500;silver=300;refined_copper=200} @{exotic_plasmonic_alloys=1000} 100 8000 3 @('industrial_production','nanotechnology')
[IO.File]::WriteAllText($path,$script:json,[Text.UTF8Encoding]::new($false))
# Packaged goods describe the actual material bill, not unsupported elemental transmutation.
$updates=@{
 cannon_round=@{Fe=78.4;C=18.6;H=3};mass_driver_slug=@{Fe=24.5;C=.5;W=75};kinetic_pd_round=@{Fe=78.4;C=1.6;W=20};
 guided_missile=@{Fe=58.8;C=18.2;Si=10;H=3;N=1.6;O=5.6;Na=2.8};
 consumer_goods=@{C=29.94;H=9.98;O=7.485;N=2.495;Al=25;Cu=15;Si=10;La=.0375;Ce=.0375;Nd=.025};
 antimatter_containment_cell=@{W=50;Y=7.5;Cu=15;O=7.5;Ni=12;Cr=4;Fe=3;Mo=1}
}
$cg=$updates.consumer_goods; $lux=@{}
foreach($e in $cg.Keys){$lux[$e]=.625*$cg[$e]};$lux.C+=15;$lux.H+=5;$lux.O+=3.75;$lux.N+=1.25;$lux.Cu+=12.5;$updates.luxury_goods=$lux
$matJson=[IO.File]::ReadAllText($materialPath)
foreach($id in $updates.Keys) {
    $script:json=$matJson;$s=Span $id;$obj=$matJson.Substring($s[0],$s[1]-$s[0]+1)|ConvertFrom-Json
    $obj.composition=$updates[$id]
    if($id -eq 'antimatter_containment_cell'){$obj.description='Empty containment hardware made from tungsten, superconducting material and high-temperature alloy. Contains no antimatter.'}
    $text=$obj|ConvertTo-Json -Depth 20;$matJson=$matJson.Remove($s[0],$s[1]-$s[0]+1).Insert($s[0],$text)
}
$end=$matJson.LastIndexOf(']');$matJson=$matJson.Insert($end,",`n"+($newOre|ConvertTo-Json -Depth 10)+"`n")
[IO.File]::WriteAllText($materialPath,$matJson,[Text.UTF8Encoding]::new($false))
Write-Output 'Production applications migrated and missing supported feedstock paths added.'
