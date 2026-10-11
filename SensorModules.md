# Sensor modules

## Hardware and research

Sensors are installed components rather than bonuses granted by a ship's role. Basic sensor arrays require electricity research. Advanced arrays require electricity, energy fields and the scanners application. The ship designer offers researched hardware and the station view queues sensor module construction through the existing material-backed assembly flow. Blueprint registration, ship construction and station module commands recheck research against the owner. Corporations use their affiliated empire's research when constructing station modules; this does not transfer blueprints between owners.

| Module | Required research | Slots | Mass | Power draw | Range | Scan strength |
| --- | --- | --- | --- | --- | --- | --- |
| Basic sensor array | Electricity | 1 | 200 kg | 10 kW | 2,000,000 km | 10 |
| Advanced sensor array | Electricity, energy fields and scanners | 2 | 500 kg | 40 kW | 200,000,000 km | 25 |

These initial ranges, strengths and component costs are provisional. Sensor mass contributes to construction materials and maneuver costs. Ship power profiles include sensor draw. Station sensor modules draw their catalog power and go offline when the station sheds nonessential loads. A client cannot submit zero mass, reduced slots or fabricated generation for a sensor station module.

## Development and installed capability

New ship blueprints capture sensor range and scan strength in ShipSensorProfile. Scanners application development can improve newly designed advanced arrays through the existing application modifiers. Earlier blueprints and ships retain their frozen capability after research and save/load. Research does not install equipment or upgrade existing ships. In-use blueprints require a new design for changes.

Older designs without a captured profile use only recognized installed sensor module IDs. The legacy scanner_array_01 ID is recognized as an advanced array. Explorer and military roles alone provide no sensor capability. Legacy modules retain catalog baseline values rather than receiving current research modifiers.

## Detection and integration

Ship sensors contribute no range or scan strength while the power snapshot records the sensor service switched off. Actual daily or tactical allocation can restore service with current supplies. Critical upkeep, cargo conditioning and scheduled burns precede sensor idle power; other equipment follows it in the provisional service order. Recorded shedding persists in save version 60 and remains independent of diplomatic sharing grants and explored history. See [ShipRepairs.md](ShipRepairs.md#power-load-shedding).

Ship sensor arrays now participate in persistent per-mount damage. Hull-penetrating general combat hits distribute the existing collateral budget across surviving weapons, shield emitters, workshops and sensors. Shields still prevent mount damage until hull penetration. Dedicated sensor subsystem targeting is not added by this change.

Live range and survey strength scale linearly with each sensor mount's remaining condition. Each capability uses the strongest working array rather than adding duplicate arrays. For captured blueprints the best condition-scaled catalog capability is divided by the best undamaged catalog capability and applied to the frozen range or strength. This preserves the saved development gain without applying current research or modifying the blueprint. The aggregate profile does not store separate developed values per mount; mixed sensor tiers use this provisional shared scaling rule. Older recognized modules use their catalog baseline. Disabled mounts, destroyed ships, recorded essential-power outages and unknown hardware contribute zero.

SensorCoverage and FleetProcessor's survey-strength query read the same live profile. Damaged arrays may lose distant contacts while fully disabled arrays provide none. Other authorized ships, online stations and direct sharing agreements can still contribute to the empire's common view. Contact loss changes interception, threat assessment, combat withdrawal and travel shield policy immediately through their existing current-coverage gates. Exploration history and sharing agreements are retained when a sensor fails; remembered contacts do not authorize actions.

Existing onboard and base repair paths recognize sensor mounts and pay their catalog material receipts and ordinary work costs. Restoring an array restores only its repaired fraction of frozen capability. Ordinary power, travel and sensor refresh never repair hardware. Station sensors retain their existing online and operational gates; per-station mount damage is not introduced. No save field or version change is needed because the existing mount-damage map already persists sensor indices.

SensorCoverage uses currently present sensor hardware for foreign fleet contacts. Previously explored systems remain known after observers leave, but their foreign fleet contacts disappear without current coverage. Hull losses, ship electrical outages, offline station modules and nonoperational stations remove their coverage. Warp and interstellar transit are excluded. ScanSystemCommand requires a live authorized sensor asset in the requested system.

The galaxy map renders and selects owned fleets or current detected contacts. Missing observer records do not reveal foreign fleets. SensorProcessor runs after daily movement and encounters; discovered systems and contacts persist through existing fog-of-war saves.

SensorPosition converts observers and targets into system-centered meters on the snapshot's campaign day. Stellar transfers retain their saved absolute position; planetary and lunar transfers add the current ephemeris position of their gravity center. Waiting ships use their actual source site and failed coast or capture keeps the saved physical trajectory. Each orbital station uses its own saved phase through StationRendezvous.at, shared by sensors, docking, phasing and parking departure. Free circular parking keeps its representative body phase. LocalFlight detection retains its frozen geometry and saved motion. Deep-space stations keep distinct static representative positions. System deep scans, anomalies and pirate discovery remain coarse system-level operations. Signal propagation delay, occlusion, stealth signatures, identification uncertainty and last-known contacts remain future mechanics.

## Common sensor views and agreements

SensorProcessor rebuilds one immutable FogOfWarState for each empire from every currently active authorized ship or station sensor. Corporate hardware belongs to its affiliated empire's native sensor pool. SensorSharing.view gives a corporation the same common home-empire view when it has no additional foreign grants. A corporation without a home empire has no implicit imperial access. Corporate operation in a foreign system does not itself grant or contribute sensor data there.

SENSOR_DATA_SHARING is an explicit reciprocal agreement between two empires or a corporation and an empire. ProposeDiplomaticPactCommand supports these parties for this agreement type; ResolveDiplomaticProposalCommand requires acceptance by the named receiver before coverage changes. A corporation's agreement with a foreign empire contributes only that corporation's own hardware and grants it access to that empire's native hardware pool. Empire agreements share each signatory's native pool, including affiliated corporations. Foreign grants do not relay third-party contacts through another agreement. Corporations with additional direct grants receive an immutable extended view under their own ID in the existing fog-of-war collection.

Accepted sensor agreements remain in the persisted diplomatic proposal ledger independently of relation tiers. They do not change tariffs, grant research or transfer blueprints. Pending proposals expire after the existing negotiation interval. Either signatory can revoke a ratified grant through RevokeSensorSharingCommand. Total war blocks sharing immediately and revokes affected ratified grants, including foreign corporate agreements through their home empire; peace does not reactivate them. Current contacts disappear on the next sensor refresh if no other authorized hardware still detects them. Permanent exploration knowledge remains.

The diplomacy view offers queued sensor proposals to empires and corporations and acceptance, rejection, withdrawal and revocation for the player's imperial signatory. Corporate receiver decisions use the same command flow. All mutations occur on the simulation tick. Save version 54 adds station orbitalPhaseRadians; existing proposal and fog-of-war fields retain sharing agreements and views without another top-level saved collection.

## Last-known contacts

Campaign combat rechecks actual shared hardware before each round and independently holds fire for a side without live coverage. Damage or power shedding from the completed round can remove targeting; another authorized observer can maintain it and reacquisition can restore it. Both-blind fleets stop further rounds without a physical escape. Co-location and remembered observations cannot start a battle. See [CombatModules.md](CombatModules.md#live-combat-targeting).

Save version 61 adds immutable observations to each shared sensor view: fleet and owner IDs, system-centered position, observation campaign day and observed funded strength. Old saves default to empty memory. Sensors refresh before protection decisions and after movement and combat so a subsequent power shortage retains the last observation. Reacquisition replaces it with the new position, day and strength. Exploration updates preserve memory.

Provisional memory lasts for three campaign days and expires when its age reaches three days. Position uncertainty grows by 86,400 km per day, corresponding to a provisional 1 km/s unknown-motion envelope. Memory contains no predicted motion or blueprint. Hidden movement, destruction and supply changes cannot update an observation. Spatial safety checks conservatively include threats whose uncertainty envelope overlaps the normal threat radius.

Recent wartime observations sustain travel shield recharge, defensive power and repair priorities and delay saved escape return when the refuge or destination remains threatened. A live contact replaces the remembered contribution so strength is never counted twice. Peace removes the safety concern without erasing the historical observation. Target acquisition, firing contact gates, interception and new escape trajectory planning still require current detections; memory does not supply hidden trajectories or authorize an attack. Existing saved escape travel continues normally.

Each viewer records only observations obtained through currently authorized hardware. Domestic corporations consume their empire's common view. Direct sharing can create observations while granted but never relays another viewer's remembered contacts. Revocation stops new observations while already received knowledge remains until expiry. New agreements do not disclose old observations from the other party.

## Contact display

Fleet cards and the galaxy map's selected system or fleet offer an expandable Sensor contacts panel. Each row distinguishes live detection from lost contact and shows the observation campaign day, age, system-centered coordinates in km and position uncertainty radius. Live rows replace remembered rows for the same fleet. Lost rows use only stored fleet and owner IDs and observation coordinates; they do not reveal hidden current names, positions, destruction or blueprints. Expired observations disappear even before another sensor refresh. A viewer sees only its authorized shared view and current hardware coverage.

Fleet panels explain when recent threats retain defensive power and repair priorities, keep travel shield recharge eligible or delay saved escape return at the refuge or destination. Funding and physical travel remain engine decisions. The panel offers no attack, interception or mutation controls and states that lost contacts cannot authorize attack or interception. Existing intercept choices continue to validate live detection. Queries run in the background against immutable snapshots, reject stale responses and preserve expansion across updates. Save version remains 61.

SensorContactCardTest checks stored coordinates without hidden fleet details, live replacement, age and uncertainty, expiry, view isolation, safety explanations, read-only rendering, expansion, failed queries and stale responses.

## Focused verification

SensorContactMemoryTest covers lost coverage, conservative safety decisions without targeting, fixed observed strength after hidden destruction, uncertainty, expiry, reacquisition without duplicate strength, escaped return, peace, save/load and old-record defaults. SensorSharingTest also checks that later agreements cannot relay historical contacts and revocation does not erase knowledge already received.

SensorDamageTest checks frozen development scaling, mixed and duplicate arrays, disabled and destroyed hardware, electrical outages, legacy aliases, shared nearby observers, real combat collateral and copper-limited onboard repair. SensorSharingTest checks damaged domestic corporate, cross-empire corporate and diplomatic observers losing live contacts while preserving agreements and exploration. CombatWithdrawalTest now removes sensor coverage through actual mount damage rather than a sensor-free test blueprint.

SensorModuleTest covers research gating, installed mass and power costs, development of new designs, frozen save/load capability, range differences, contact loss, scan presence, station catalog enforcement and electrical outages. Existing sensor, command, ship optimization and design validation checks cover integration without a balance simulation.

OrbitalSensorCoverageTest covers stellar waiting, coast, capture and arrival, planetary and lunar frame translation, false contacts between different gravity centers, missing bodies, current tick epoch, failed capture and save/load. SensorProcessor's snapshot entry point preserves the campaign day during the live engine tick; its older list entry point retains a day-zero compatibility default.

SensorSharingTest covers affiliated corporate fleet and station contributions, common view access, unaffiliated corporations, reciprocal direct agreements, no third-party relaying, negotiation rejection and expiry, revocation, war, persistence and blueprint isolation. SensorSharingCardTest verifies that proposal, acceptance and revocation only stage commands. StationEphemerisTest checks distinct same-altitude station positions, shared sensor and travel geometry, funded rendezvous after reload, station copies and stable phase defaults for older records.
