# Limited mixed weapon scenario

MixedWeaponScenarioTest runs repeatable two-round tactical encounters with seed 42. One ship carries a pulse laser, particle beam, ion weapon and mass driver against a target with charged shields, 10 cm armor and sensors. Point-defense variants add one laser, kinetic or ion mount. Weapon hardware and ammunition requirements come from technological applications captured into the fixture blueprints. Production technology values remain unchanged.

## Observed outcomes

These are mechanics checks using the current provisional values, not a balance assessment.

| Encounter | Observation |
| --- | --- |
| Close range, no point defense | All three beams hit in round one. The first slug arrives in round two. Defender ends with 743 hull and empty shields. |
| Close range, laser point defense | The first slug is intercepted before impact. Beam hits still resolve. Defender ends with 820 hull. |
| Close range, kinetic point defense | The first slug is intercepted using one 5 kg cartridge. One cartridge remains. Defender ends with 820 hull. |
| Close range, ion point defense | The physical slug has no electronics and cannot be intercepted by ion defense. Defender ends with 743 hull. |
| Longer range, 87.5 km | Pulse lasers hold fire. Particle and ion beams hit immediately and the first slug arrives in round two. |
| Four shield emitters, 75 km | Ion drains shields without hull or electronics damage while shield-blocked. Kinetic bypass removes 26.95 hull while 97 shields remain. |
| Four emitters, test-only zero bypass | Changing only the kinetic application's bypass fraction keeps hull intact behind the surviving shields. |
| Test-only application edits | Reduced laser range holds fire, ion electronics multiplier 0.5 gives 40 then 50 actual electronics damage and 7 kg slugs consume a 14 kg magazine. Older captured hardware keeps its original values. |

Close range is half the shortest installed offensive range, currently 12.5 km. The longer-range probe derives its separation from the laser and ion range values. Ion hits consistently report zero hull damage. Component condition falls from overload and ordinary physical collateral; the no-defense close-range target loses both active mounts in round two. Armor absorbs the low-strength laser pulses individually and reduces physical beam and kinetic damage.

All runs carry two whole slugs. Both launches spend ammunition and power even when point defense intercepts a slug or the engagement ends before delivery. The second slug remains in flight when the two-round encounter stops and is discarded without a refund. Source snapshots retain their initial stores and healthy hardware. Repeating the baseline encounters with the same seed produces identical results.

## Rerun and inspect

From the repository root with Java 27 configured:

```powershell
mvn -q -pl engine test '-Dtest=MixedWeaponScenarioTest'
```

The test regenerates these reports with actual arena logs, remaining stores and mount conditions:

- [Close-range defense variants](engine/target/mixed-weapon-scenario.md)
- [Longer-range encounter](engine/target/mixed-weapon-range-scenario.md)
- [Surviving shields and bypass edit](engine/target/mixed-weapon-shield-scenario.md)
- [Application-edit comparison](engine/target/mixed-weapon-application-scenario.md)

Reports under `target` are build outputs and disappear after Maven clean. This summary records the current observations. Future balance edits belong in the technological applications; rerun the scenarios with freshly captured hardware afterward. The JSON edits in these tests are local copies and never rewrite the production catalog.

## Mixed fleet and recovery probes

MixedFleetApplicationScenarioTest uses four ships with one offensive weapon family each. Seed 42 reproduces every baseline. Each encounter stops after two rounds and the kinetic ship carries two whole slugs. A target with sixteen charged emitters keeps shields standing at close range so kinetic bypass can be observed separately from ordinary shield depletion. The target has 10 cm armor. A test-only zero-bypass application keeps hull health intact behind those shields.

Additional probes change local application copies to shorten laser range, set beam accuracy to zero and remove ion electronic overload. A fresh capture changes combat behavior while repeated baseline captures remain identical. At 450 km, a deliberately extreme target acceleration stresses tracking: the particle beam hits in both rounds, lasers hold outside their range and the kinetic projectile misses. Removing the kinetic application's range and acceleration penalties and setting accuracy to one produces a hit. This artificial acceleration isolates the accuracy rule and does not represent a balanced fighter design.

MixedFleetRecoveryScenarioTest starts four weapon ships guarding a repair station and resolves at most ten rounds against funded ion hardware. All four ships survive with their original hull health. Ion fire removes 800 electronics durability points, disabling their weapons and drives. The station cannot restore anything without local repair materials. Supplying finite market stock allows fifty points of paid work per day, completing repair in sixteen days. The test verifies every material debit and the exact material plus labor cost. Queued purchases then draw approximately 435.56 kWh from finite grid storage before recovery restores the original guard mission. Repair itself grants no battery charge.

The recovery probe uses tactical combat, queued service and the existing base repair and mission recovery processors in isolation. Station staffing and electrical profiles are fixed fixture inputs. It does not run the full economic simulation or strategic AI and it makes no claim about optimal fleet composition. The role probes use abundant firing power; the recovery probe uses finite batteries and generation set to equipment upkeep. These inputs and generous target hull health keep the checks bounded without editing production technologies.

Run both new suites from the repository root:

```powershell
mvn -q -pl control -am test '-Dtest=MixedFleetApplicationScenarioTest,MixedFleetRecoveryScenarioTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Generated reports contain actual actions and paid recovery totals:

- [Standing shields and bypass](engine/target/mixed-fleet-shields.md)
- [Application changes and range](engine/target/mixed-fleet-applications.md)
- [Agile target tracking](engine/target/mixed-fleet-tracking.md)
- [Ion combat leading to recovery](control/target/mixed-fleet-combat.md)
- [Paid repair and charge totals](control/target/mixed-fleet-recovery.md)

## Normal campaign lifecycle

CampaignCombatLifecycleTest uses normal SpaceConquestEngine turns with combat seed 42. Four separate ships carry captured pulse-laser, particle-beam, ion and mass-driver hardware. One combat round causes hull and electronics damage, spends the kinetic magazine and commits funded withdrawal. The saved guard stays suspended through actual station engineer payroll, finite material repairs and a surface ammunition purchase with paid rocket lift. It resumes on turn 20 after purchasing one application-defined 20 kg slug and spending about 1,334 kg of maneuver propellant. A missing-stock variant completes electronics repair but cannot restore the guard or invent ammunition.

Disk checkpoints cover withdrawal, partial repair, blocked resupply and the restored guard. Recovery after the partial-repair reload matches uninterrupted ticks for fleets, hardware, stations, grids, markets, empires and both histories. The targeting probe puts an empty kinetic ship ahead of a funded beam ship in the enemy roster. Ion attacks remove funded enemy offense without hull damage; over ten rounds actual incoming hull damage is about 271 against an opening funded salvo of 180 per round. Opening attacks remain simultaneous.

These are bounded integration checks, not weapon balancing or blueprint construction legality checks. Fixture ships add captured reactors to supply their weapons. Only AI service commands are queued during recovery; fleet procurement and trade planning are excluded. The seed fixes combat for these opening encounters and does not persist RNG continuation. Production application values are unchanged.

```powershell
mvn -q -pl control -am test '-Dtest=CampaignCombatLifecycleTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Generated receipts: [Campaign combat lifecycle](control/target/campaign-combat-lifecycle.md) and [Campaign targeting](control/target/campaign-targeting.md).

## Scope

SurfaceMixedWeaponScenarioTest adds campaign probes with a pulse laser, particle beam, ion weapon and mass driver battery, each carrying two funded salvos. A fixed seed reproduces no-defense, laser-defense and ion-defense encounters at half the captured laser range. Beams still damage ships with projectile defense and paid receipts retain every launch and store debit. A longer-range probe holds lasers and demonstrates kinetic hull damage while eight shield emitters remain charged. Ordinary physical collateral can damage shield mounts, so mixed component damage is not attributed entirely to ion hits. The probes use live campaign coverage and the existing ten-round bound. They regenerate [Mixed surface weapon scenarios](engine/target/mixed-surface-weapon-scenarios.md).

SurfaceBatteryLifecycleTest saves an actual ion encounter to disk, reloads disabled electronics without hull damage, commits a partial paid repair and reloads again before completing repair. It purchases a real slug, charges a support ship from finite grid storage and transfers that stored energy into the battery. A saved rematch spends the supplied round and keeps the first encounter's receipt unchanged. Additional checks cover old history without receipts and the saved funded-withdrawal outcome. No production balance values change.

CombatApplicationValidationTest checks all current combat and auxiliary definitions and rejects missing capabilities or invalid local JSON edits with application-specific errors. Existing blueprints retain captured values and older saves retain their compatibility defaults. To rerun the mechanics, lifecycle and validation checks together:

```powershell
mvn -q -pl engine test '-Dtest=MixedWeaponScenarioTest,SurfaceMixedWeaponScenarioTest,SurfaceBatteryLifecycleTest,CombatApplicationValidationTest'
```

The fixture supplies live targeting explicitly, abundant electrical output and a fixed separation. It holds optional shield recharge and stops after two rounds through the existing withdrawal callback. It does not evaluate movement, acquisition, construction legality, fleet economics or sustained combat. Sensor disablement cannot remove externally supplied contact in this fixture; live sensor-contact tests cover that gate separately. Round damage totals remain raw landed weapon output, while the per-hit logs show actual shield, armor, hull and electronics effects.
