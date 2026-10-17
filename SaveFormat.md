# Save format

The save format starts again at **version 1**, reset on 13 October 2026. There are no earlier campaign saves to preserve. This baseline contains the complete current game state, including research, application development, industry, transport, ships, combat history and fleet service receipts.

SaveGameManager requires an explicit integral JSON `version` equal to `SaveGame.CURRENT_VERSION`. Missing, non-integral, historical and unsupported future versions are rejected before their payload is deserialized. SaveGame also enforces the current version when constructed directly. Earlier development versions have no supported migration path.

Loading preserves captured ship hardware and ammunition requirements exactly. It no longer retrofits old weapon definitions. Campaign start metadata is preserved when valid; missing or damaged optional metadata uses the current GameClock calendar rather than a historical version-dependent date.

Current state also retains immutable supply development plans: source and recipient hubs, material, procurement quantity, initial source-market funding, assigned carrier and closure state. Current-format reload preserves this intent alongside the actual construction, market accounts, routes and paid cargo. Reloading a plan does not transfer its funding again. Earlier development saves do not require migration support.

Defaults for optional current fields and source-level convenience constructors remain useful for empty game state, uninitialized receipts and application catalogs. They are not a promise to load older save formats. Earlier version numbers in design reports and implementation history describe development before this reset; this document and the current source define the supported boundary.

Optional research decision metadata and bounded research history receipts are now included. Missing metadata is shown as an unrecorded reason and missing history defaults to an empty list. These additive fields retain format version 1 and do not introduce historical version migrations.

Ship manufacturing profiles also capture an immutable map of linked application modifiers. A missing map defaults to empty; linked components without recorded modifiers compare against baseline values when considering a new AI order. This optional addition retains version 1. Loading does not recalculate captured ship hardware or existing construction quotes.

Future incompatible format changes increment the version from this baseline. Resource tuning and changes that do not alter the serialized contract do not need a version bump. Migration support should only be added when an actual saved campaign needs it.

SaveFormatTest verifies the version 1 round trip and rejects unsupported or malformed version metadata before inspecting the payload. Existing focused persistence tests cover current research, materials, ships, ammunition, module damage, auxiliary forces, targeting history, fleet service and campaign dates.

Survey teams retain captured application capabilities, installation work, partial passes, per-body orbital coverage, fleet reservations, last-worked ticks and blockers. Payroll reserves remain in industry accounts. Finite hidden deposits are generated with the new galaxy and persist through ordinary geological-deposit serialization. Current-format survey reloads resume work without replaying purchases or rerolling completed passes.
