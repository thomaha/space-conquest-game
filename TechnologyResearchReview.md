# Technology research review

Reviewed on 12 October 2026. Scope: research commands, daily progression, outcome decisions, AI selection, technology UI, catalog references, production integration and persistence. The original review made no production source changes. The findings below describe that reviewed state; subsequent fixes are recorded at the end.

## Findings

### High priority: Salvage grants repeatable unvalidated research

`ReverseEngineerSalvageCommand.validate` checks only the empire and positive component count. It does not verify catalog membership, prerequisites, physical salvage, ownership, location or previous consumption. Its compatibility constructor ignores the supplied debris ID. The command creates missing projects as foundational research with one scientist, including when the target is an application. `ResearchTickProcessor` later unlocks the project ID without revalidating it.

Reproduction using the actual command and tick processor: an empire with no salvage assets or scientist economies submitted the same intact component claim three times for `nonexistent_technology`. The project gained 1,050 points and the next research pass unlocked that unknown ID. The player technology page exposes this path through a button with a fixed debris identifier and target `energy_shielding`; that target is absent from the current catalog. The displayed salvage mission's 42% progress is also a constant.

Sources: [Salvage command](control/src/main/java/com/spaceconquest/control/command/ReverseEngineerSalvageCommand.java), [Completion processing](engine/src/main/java/com/spaceconquest/engine/technology/ResearchTickProcessor.java) and [Technology view](frontend/src/main/java/com/spaceconquest/frontend/TechnologyView.java).

Recommendation: Disable unsupported salvage actions until an owned physical inventory and one-time consumption receipt exist. Validate target identity and prerequisites and preserve the actual application/foundation project type. Completion should independently reject invalid targets.

### High priority: Research output is disconnected from actual scientist work

Starting a project counts `SystemEconomy.employedScientists`, while household processing separately caps public scientist jobs by budget and actually fills profession vacancies. The system economy scientist quota has a minimum of ten even at zero population or zero education efficiency. Once research starts, each tick uses the saved assigned count without checking current staffing, payroll, population or ownership. Research runs before that day's economy processing. Training is passed as a constant 1 and species intelligence comes from the empire's primary race rather than its working scientist cohorts.

Reproduction: an empty default system economy reported ten scientists and accepted a five-scientist project without population or household payroll. Removing all system economies afterward still produced five research points in the next research pass.

Sources: [Assignment validation](control/src/main/java/com/spaceconquest/control/command/StartResearchCommand.java), [Research output](engine/src/main/java/com/spaceconquest/engine/technology/ResearchProcessor.java), [Economy quotas](engine/src/main/java/com/spaceconquest/engine/economy/SystemEconomyProcessor.java), [Public job funding](engine/src/main/java/com/spaceconquest/engine/economy/HouseholdEconomyProcessor.java) and [Daily turn order](engine/src/main/java/com/spaceconquest/engine/SpaceConquestEngine.java).

Recommendation: Define one authoritative paid scientist capacity, allocate it across projects each tick and pause unfunded work while preserving requested assignments and progress. Align processing with the selected payroll timing. Actual training and species output can follow once that capacity contract is established.

### High priority: AI does not develop applications

`EmpireAIController.manageResearch` selects the first eligible unresearched foundational technology with five scientists. It never starts application research or refinement and never resolves pending outcomes. Because ship and weapon applications have separate research gates, unlocking their parent technology does not make them usable. Starting-era applications can mask this gap. The AI also has no fallback allocation when fewer than five scientists are available.

Source: [AI research selection](control/src/main/java/com/spaceconquest/control/ai/EmpireAIController.java).

Recommendation: Plan foundations and applications together around current fleet and economic needs, allocate actual available scientists and consume pending research decisions through ordinary commands.

### Medium priority: Research difficulty is hardcoded

`StartResearchCommand` assigns every foundational project 1,000 points and every application or refinement 500 points. Foundational discovery complexity is never used to calculate research cost. Outcome probabilities and path modifiers are also constants in `ResearchProcessor`.

Reproduction: electricity at complexity 1 and warp at complexity 10 both require 1,000 points. Antimatter and gravitational engineering at complexity 9 do too.

Sources: [Project cost](control/src/main/java/com/spaceconquest/control/command/StartResearchCommand.java), [Technology schema](engine/src/main/java/com/spaceconquest/engine/Technology.java) and [Outcome policy](engine/src/main/java/com/spaceconquest/engine/technology/ResearchProcessor.java).

Recommendation: Give technologies and applications explicit research costs and put shared outcome policy in resource data. Keep research cost separate from manufacturing complexity, then capture the quoted cost when a project starts.

### Medium priority: Scientists cannot be released from a project

The start/update command rejects zero scientists and no pause or cancellation command was found. The UI checks new counts against unassigned scientists without crediting the project's current allocation. With all scientists assigned, even reducing an existing project's count is blocked by the view, although the command's positive-count replacement logic can support a reduction.

Sources: [Assignment command](control/src/main/java/com/spaceconquest/control/command/StartResearchCommand.java) and [Technology view](frontend/src/main/java/com/spaceconquest/frontend/TechnologyView.java).

Recommendation: Add explicit paused assignments and cancellation through queued commands. Permit reassignment using the current project's reserved workers while retaining progress.

### Medium priority: Technology exchange is incomplete

Exchange records and the UI advertise prepopulated progress, but starting research always begins at zero and progression only reads the speed bonus. A route with 50% prepopulation yielded zero initial progress in the probe. No production command establishing exchange routes was found. Active bonuses are not checked against current diplomatic eligibility or whether the sender knows the shared target.

Sources: [Exchange record](engine/src/main/java/com/spaceconquest/engine/technology/TechnologyExchangeRoute.java), [Project creation](control/src/main/java/com/spaceconquest/control/command/StartResearchCommand.java) and [Route bonus use](engine/src/main/java/com/spaceconquest/engine/technology/ResearchProcessor.java).

Recommendation: Treat exchange as unfinished until accord creation, eligibility checks and one-time progress grants are connected. Avoid advertising an applied grant when it has not been committed.

### Medium priority: The view advertises a nonexistent generic technology bonus

Every technology card displays a fixed +5% theoretical scientific progression efficiency. Research output does not consume that bonus and the message appears before research as well as afterward. This makes the visible benefit differ from the actual unlock and application mechanics.

Source: [Technology card](frontend/src/main/java/com/spaceconquest/frontend/TechnologyView.java).

Recommendation: Display actual unlocked applications and implemented effects from authoritative data.

## Working foundations

- Ordinary research start validation checks parent technologies and application prerequisites and rejects unknown targets.
- Recorded application outcomes require decisions, cannot be consumed repeatedly and accumulate accepted multipliers. Complexity has a floor of one. Documented legacy path selection replaces the baseline variant rather than accumulating free upgrades.
- Application progression and pending decisions survive saves. New ship blueprints capture researched hardware and manufacturing modifiers while existing designs retain their captured values.
- Current integration tests exercise industrial output, manufacturing quotes and weapon application improvements.
- The catalog contains 30 technologies and 121 applications, with no duplicate IDs or unresolved prerequisite references in the combined technology/application namespace. This check does not establish that every application has a complete runtime consumer.

## Verification and recommended order

The existing focused suite passed all 21 tests: `ResearchProcessorTest`, `ResearchCommandTest`, `ApplicationRefinementIntegrationTest`, `ApplicationOptimizationIntegrationTest` and `WeaponApplicationOptimizationTest`. Passing tests do not cover the demonstrated funding, salvage or AI application gaps.

The additional read-only probe is [ResearchReviewProbe.java](control/target/ResearchReviewProbe.java). It uses existing compiled classes and the real command and research processors; it does not modify the catalog or campaign saves.

Fix salvage validation and scientist funding first. Then connect AI application development, project reassignment and catalog research costs. Complete exchange only once the core lifecycle is reliable. Use bounded tests for repeated salvage, lost staffing, competing assignments, paused progress and AI application decisions before balancing research speed.

## Implemented fixes

Follow-up on 12 October 2026 addressed all seven findings:

- Disabled unsupported salvage claims and the misleading salvage controls. Completion independently rejects unknown targets and suspends targets with missing prerequisites.
- Moved research after daily payroll, removed empty-system scientist floors and introduced physical paid scientist capacity with proportional shortage allocation. Actual species and education levels affect output; Hive societies use physical scientist cohorts instead of currency payroll.
- Connected AI application discovery, paid refinement, paused-project resumption and one-time outcome decisions.
- Added explicit discovery and refinement costs throughout the catalog and a validated resource policy for fallback costs, outcome probabilities, path modifiers and exchange values. Existing project quotes remain fixed.
- Added queued assignment updates, zero-assignment pause and explicit cancellation with UI controls that remain usable when all workers are reserved.
- Added source-authorized sharing and revocation under existing eligible alliances, prerequisite and source-knowledge checks, a single opening grant and funded speed bonuses that suspend when eligibility is lost.
- Replaced the generic theoretical bonus and fixed progress claims with actual costs, unlocks and configured optimization values.

All 72 focused tests passed across commands, real payroll timing, shortages, mixed-species output, Hive capacity, persistence, exchange suspension and revocation, AI lifecycle, resource edits, JavaFX controls and existing application, economy and combat integrations. The final assignment-shortage adjustment also passed its 20-test command regression subset. Physical salvage recovery, paid exchange negotiation and full research balance remain future work. See [TechnologyMechanics.md](TechnologyMechanics.md) for current behavior.
