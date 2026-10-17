# Corporate ship designs

Corporate ship procurement now uses ShipBlueprintFactory, the same physical blueprint evaluation used by the player designer. Affiliated corporations in compatible societies retain their own ownership and use their empire's research. Player design commands continue to accept only empire owners.

## Model selection

CorporateShipDesigner tries a bounded set of freely sized cargo models. Hull capacity follows chosen component occupancy and optional spare capacity. Repeated cargo holds and tanks are permitted within application-defined hull and manufacturing limits. No named ship class sets hull size or hardpoints.

The preferences and search bounds live in engine/src/main/resources/corporate_ship_design_policy.json. The first researched drive preference with a valid candidate is used. Within that family, selection minimizes unmet cargo demand then captured construction work and dry mass. These are AI preferences; physical capabilities and engineering limits come from the shared catalogs and technological applications. No weapon balance values change.

Equivalent captured designs are reused. Changed research creates a new model without rewriting existing ships or pending construction receipts. New corporate models retain individual installation identities, hardware, hull frames and frame, armor, component and integration production receipts. Those captures support the existing refit, replacement and retirement paths through version 1 saves.

## Paid procurement and first transport model

Corporate fleet investment replaces the previous fabricated dry mass, launch threshold and structural rating with the factory's calculations. Orbital hubs resolve through the shared construction site lookup. Invalid candidates create no model, order or charge. The existing 12,000-credit corporate procurement charge remains separate from later material purchases and yard fees.

After repeated facility shortages, transport AI can request a new corporate model even when the company has none registered. An empty design ID denotes this request inside RequestAIFacilityTransportBuildCommand. Discarded previews register nothing in the live state. Execution repeats model selection, actual material and service quotes, company reserve checks, operating profit and payback checks before registering the selected model and queuing paid construction. Existing public models remain the fallback.

The selector currently supplies cargo equipment. Extraction-oriented mining procurement remains unavailable until real mining equipment exists in the shared component catalog; it cannot fabricate a mining capability to pass role validation. Physical geometry, richer route-specific model optimization and reusable removed components remain future work.

## Limited verification

CorporateShipLifecycleTest checks variable capacity, repeated equipment, research limits, equivalent-model reuse, corporate ownership, player command restrictions and capture persistence. Its company-funded scenario follows partial construction, enlargement refit, paid replacement and AI retirement through disk reloads. Recovery debits the local buyer and preserves the replacement and corporate asset list.

AITransportConstructionTest checks first-model procurement without an existing company blueprint and prevents duplicate orders. Existing corporate research optimization tests now verify actual factory launch mass rather than the former fabricated threshold. Related construction, research, refit and retirement suites provide regression coverage. These are bounded correctness checks, not a weapon or economy balance study.
