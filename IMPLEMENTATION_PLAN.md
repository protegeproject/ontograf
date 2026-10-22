# OntoGraf implementation plan

Enable persistent relationship labels in the Protégé Desktop graph, then improve relationship correctness, responsiveness, compatibility and navigation. Deliver the work as small, independently reviewable changes with working plugin builds at each milestone.

The first release should let a user display readable edge labels, filter relationships without corrupting the graph, and continue navigating after ontology edits. Retain the existing Swing, Piccolo and Cajun architecture for this release. Evaluate a renderer replacement only after measuring the limitations of that implementation.

## Execution status

The working tree now contains an implementation and automated tests for the core
label, relationship model, navigation and persistence changes. The user has
validated the plugin in **Protégé Desktop 5.6.9**, including displayed axiom labels
and the Labels dropdown width fix. The dropdown now uses its preferred content
width instead of stretching across the toolbar.

Selected mode intentionally hides labels when selection is cleared; All keeps
them visible regardless of selection. Both modes still hide text below 45% zoom.
The toolbar tooltip and documentation now explain this distinction.

This confirms desktop operation for the tested features. The broader release
checks below remain open, including other layouts, DPI/themes, lifecycle,
persistence and cross-platform coverage. See [validation evidence and the
smoke-test checklist](docs/VALIDATION.md).

| Package | Implemented | Remaining acceptance or work |
| --- | --- | --- |
| 0 | Baseline artifact, pinned API/runtime, fixture and automated suite; user-confirmed plugin operation in Protégé 5.6.9 | Imports-view smoke tests and broader host checklist |
| 1 | Non-destructive filtering through an immutable index, safe anonymous-target omission, isolated roots, literal search, debounced refresh, disposal and import-cycle protection | Desktop lifecycle and change-event tests |
| 2 | Labelled arc subclass, Off/Selected/All preference, path geometry, zoom threshold, full descriptions and image rendering tests; labels and content-sized dropdown validated by the user in Protégé 5.6.9 | All layouts, DPI/themes and remaining interaction checks |
| 3 | Semantic edge keys, typed entity identity, shared incident index, axiom/ontology provenance and structured styles | Broader real-ontology semantic review |
| 4 | Java 11 target, API 5.6.6, provided host dependency, pinned build plugins, CI and bundle cleanup; successful user testing in Protégé 5.6.9 | Detailed host-log review, release tooling and cross-platform validation |
| 5 | Indexed lookup, cancellable background refresh from snapshots, stale-result guards, expansion budget and synthetic benchmark | Targeted invalidation, asynchronous first-use discovery/layout, real-ontology UI/heap benchmarks and collision suppression |
| 6 | Edge inspector, selectable/copyable details, pinning, Back/Forward, optional axiom annotation labels and legend | Dedicated property copy/navigation actions and tested inferred mode |
| 7 | Validated versioned UTF-8 state, atomic replacement, legacy reader, missing-entity reporting and corrected DOT | Full host round trip, SVG investigation |
| 8 | Named-filler object cardinalities and object hasValue | Literal/data-property nodes, anonymous individuals, complex expressions, Protégé 6 evaluation and renderer comparison |

The first candidate deliberately uses asserted axiom projections. It does not
silently substitute the host's inferred hierarchy. Remaining inferred-mode and
expression-node work must preserve this explicit distinction.

## Scope and working decisions

- Interpret the initial label feature as visible descriptions of relationships, using Protégé entity rendering (`rdfs:label` when configured). Support labels from axiom annotations later, once supporting axioms are retained in the model.
- Offer Off, Selected and All label modes. Use Selected initially to avoid overwhelming existing graphs; remember the user's choice. Selected means edges incident to selected nodes, plus any independently selected edge supported by the renderer.
- Preserve current relationship wording and direction in the first implementation. Introduce shorter wording only after direction and semantics have tests.
- Treat domain/range connections as summaries of constraints. Do not present them as assertions that instances of two classes are related.
- Target an explicitly pinned stable Protégé 5.6.x distribution first. Confirm its Java and OWLAPI requirements during the baseline work. Keep a Protégé 6 migration separate because its OWLAPI transition affects source and binary compatibility.
- Keep existing graph files readable. Introduce a versioned successor when saving additional view state.
- Implementation begins with package 0 below. This plan does not require all later features to ship together.

## Source map

| Responsibility | Current source |
| --- | --- |
| View, search controls and toolbar | `src/main/java/org/protege/ontograf/OntoGrafView.java` |
| Graph setup, styles, layout and navigation | `src/main/java/org/protege/ontograf/common/GraphController.java` |
| OWL relationship discovery and caches | `src/main/java/org/protege/ontograf/common/ProtegeGraphModel.java` |
| Graph interaction | `src/main/java/org/protege/ontograf/common/ProtegeInputEventHandler.java` |
| Persistence and export | `src/main/java/org/protege/ontograf/actions/` |
| Import graph lifecycle | `src/main/java/org/protege/ontograf/OntoGrafImportView.java` |
| Dependencies and bundle configuration | `pom.xml`, `plugin.xml` |

The current model creates edge descriptions and DOT export writes them as labels. Cajun 1.0.2 provides arc paths, arrowheads, icons and tooltips but no persistent edge-text facility. Adding a toolbar toggle alone will therefore not enable canvas labels.

## Delivery order

| Package | Outcome | Prerequisites |
| --- | --- | --- |
| 0 | Reproducible build and test fixtures | None |
| 1 | Correct filtering, navigation and cache lifecycle | 0 |
| 2 | Persistent labels and preferences | 0; release requires 1 |
| 3 | Stable relationship identity and shared extraction | 1 and 2 |
| 4 | Supported build and reliable plugin lifecycle | 0; integrate with 1 through 3 |
| 5 | Measured performance improvements | 3 and 4 |
| 6 | Axiom inspection and richer navigation | 3 and 4 |
| 7 | Durable graph state and reliable export | 2 and 3; incorporate 6 settings |
| 8 | Additional OWL constructs and future host support | 3 through 7 |

Packages may be developed in separate branches where dependencies permit, but integrate and validate them in the order above. Keep compatibility changes separate from rendering and semantic changes so regressions can be isolated.

## Package 0 Establish the baseline

### Tasks

1. Record the starting commit, JDK, Maven version, dependency tree and generated OSGi manifest.
2. Build the existing project without changing dependencies first. Record any failures and make the minimum changes needed for a reproducible baseline.
3. Select and pin the supported Protégé distribution. Install the plugin into a disposable distribution and check both OntoGraf views. Ensure the bundled OntoGraf does not conflict with the candidate bundle.
4. Add a test framework compatible with the selected build. Keep extraction tests independent of Swing where possible; use real OWLAPI objects rather than extensive API mocks.
5. Add small ontology fixtures covering subclass and class assertions, object-property assertions, some/all restrictions, equivalent classes, intersections, imports, domain/range constraints, and isolated classes.
6. Add edge cases: duplicate labels on different IRIs, anonymous individuals, inverse properties, self-loops, parallel relationships, quoted and non-ASCII labels, and an ontology without a declared ontology IRI.
7. Record current edge direction, filters, search, graph save/load and image export behavior. Distinguish intended compatibility from defects to fix.

### Completion criteria

- A clean checkout has a documented build and plugin installation procedure.
- Automated fixture loading and basic relationship assertions run locally and in CI.
- The selected host starts with the candidate plugin and both views can be opened.
- Baseline limitations and a repeatable manual smoke checklist are recorded.

## Package 1 Fix correctness and graph refresh

### Tasks

1. Change `loadUnreifiedRelations` to filter into a new result collection. Never remove entries from the relationship cache during visibility checks.
2. Handle anonymous individual targets explicitly. Initially omit unsupported targets safely and document that limitation; do not cast them to `OWLNamedIndividual`.
3. Preserve requested root nodes in `showNeighborhood`, including search matches with no edges. Reconcile edges as well as nodes when replacing a neighbourhood.
4. Quote literal search text for contains, starts-with, ends-with and exact-match modes. Keep regular expressions exclusive to regexp mode. Report invalid patterns and restore the cursor in a `finally` block.
5. Define cache invalidation for axiom additions/removals, active ontology changes, imports changes and rendering changes. Refresh the ontology scope rather than retaining an obsolete initial scope.
6. Start with safe complete invalidation and a debounced refresh of the visible graph. Preserve selection and positions where entities still exist. Optimise invalidation in package 5.
7. Initialise and reset expansion restrictions explicitly. Replace ambiguous substring matching with exact relationship selection where the existing representation allows it.
8. Retain references to registered listeners and introduce controller/model disposal. Invoke disposal from both applicable views and cancel pending refresh work.

### Validation

- Hide and restore an object-property relationship repeatedly: the same edges return without exceptions.
- Add and remove an axiom while the view is open: visible edges and expansion counts update.
- Change a label and switch the active ontology: the graph does not reuse stale names or relationships.
- Search for an isolated entity and literal text containing regexp metacharacters.
- Open and close the view repeatedly: listener registrations return to baseline after disposal.

### Completion criteria

Regression tests reproduce the original defects and pass after the fixes. The graph remains usable after ontology edits, filtering and view recreation.

## Package 2 Add persistent relationship labels

### Rendering investigation

1. Identify the Cajun factory or extension point that constructs displayed arcs. The temporary arcs returned by relationship discovery are not sufficient: `addArcsToModel` creates the displayed instances separately.
2. Prototype a labelled arc subclass or a controller-managed label layer using Piccolo text nodes. Prefer the smallest extension that can track path changes, filtering and removal.
3. Verify that the extension receives geometry updates during node dragging and animated layouts. Check scene-graph bounds, hit testing and image export before committing to the approach.
4. If the necessary hooks are inaccessible, make a small, separately versioned Cajun patch with source and licensing retained. Avoid copying an entire renderer into the plugin.

### Feature implementation

1. Introduce a label formatter separate from relationship discovery. Initially use the existing relationship type text.
2. Position labels near the midpoint by path length, offset from the path. Handle curved parallel edges and self-loops explicitly.
3. Render horizontal readable text with padding and a background halo. Limit label width and expose the full text in a tooltip.
4. Add Off, Selected and All controls to the OntoGraf toolbar and persist them through Protégé preferences.
5. Suppress unreadable text at distant zoom levels while keeping full descriptions available through interaction. Do not change edge visibility when hiding text.
6. Make label text non-intercepting unless it deliberately forwards interaction to its edge. Preserve node selection, dragging and context menus.
7. Remove labels when edges disappear; update them when rendering preferences or entity annotations change.
8. Include visible labels in image export and document the control in the README.

### Validation

- Check every label mode on straight, parallel and self-loop edges.
- Drag nodes and run each supported layout; labels remain attached to the correct paths.
- Exercise filtering, collapse, clear, reload and ontology changes; no orphan labels remain.
- Check long, non-ASCII and duplicate property labels, light/dark host themes where supported, and high DPI displays.
- Export a labelled image and compare its contents with the visible graph.
- Confirm arrowhead direction agrees with the displayed phrase, including inverted arcs.

### Completion criteria

The label feature works inside the pinned Protégé host, persists its preference across sessions, and introduces no navigation regressions. Package 1 and this package form the first feature release after the relevant package 4 compatibility checks.

## Package 3 Separate OWL relationships from presentation

### Tasks

1. Introduce immutable relationship data independent of Cajun nodes and arcs. Include typed source/target identity, relationship kind, property expression, restriction kind and cardinality where relevant.
2. Define an edge key from semantic fields. Entity identity must include entity type as well as IRI to handle OWL punning. Preserve inverse-property direction explicitly.
3. Store supporting axioms and their source ontologies separately from the edge key. Aggregate semantically identical edges while retaining every source; do not merge relationships solely because labels match.
4. Represent domain/range summaries with their supporting constraints and a distinct relationship kind. Preserve multiple domain/range axioms as constraints rather than presenting them as alternative asserted links.
5. Build incoming and outgoing indexes from the same extraction results. Replace the differing incoming/outgoing restriction walkers.
6. Use a visitor or equivalent explicit dispatch for supported expressions. Unsupported expressions must not silently become a different restriction type.
7. Generate labels, filters, styles and expansion menus from structured relationships. Stop using string fragments such as `Equivalent` to determine semantics.
8. Keep the rendering adapter thin: it converts relationship data into Cajun arcs and maps interactions back to relationship data.

### Validation and completion criteria

- Two distinct properties with the same display label remain distinct edges and filter entries.
- Changing a label does not change edge identity or duplicate edges.
- Incoming and outgoing navigation discover the same supported relationship from either endpoint.
- Some/all restrictions, intersections, equivalent-class conditions and inverse properties retain their meaning.
- Duplicate supporting axioms across ontologies produce a stable visual edge with complete provenance.
- Model tests run without constructing the graph canvas.

## Package 4 Modernise the build and lifecycle

### Tasks

1. Pin a reproducible Maven toolchain and modernise compiler, bundle and test plugins in small changes. Set the bytecode target to the minimum supported host runtime.
2. Align Protégé and OWLAPI dependencies with the pinned host. Audit dependency scopes and embedded jars to avoid competing copies of host APIs.
3. Inspect generated OSGi imports, package version ranges, bundle activator and embedded Cajun contents. Test installation and resolution inside the real host.
4. Add CI for build, model tests and artifact creation, with checks on the supported minimum runtime and another host-supported runtime.
5. Remove obsolete build paths and tooling only after checking whether they remain part of release packaging. Consolidate instructions around the verified build.
6. Replace stack traces and silent failures in user actions with contextual logging and useful error messages. Ensure workers, listeners and graph activities are disposed.
7. Document supported host versions, runtime requirements, installation, label controls and known OWL coverage.

### Completion criteria

CI produces a plugin jar that loads in the selected host without bundle resolution errors or duplicated API classes. Repeated view creation and disposal leaves no accumulating listeners or running tasks.

## Package 5 Optimise measured bottlenecks

### Benchmark setup

Use small fixtures for correctness, a representative user ontology, and synthetic ontologies with independently varied entity count, edge count, high-degree nodes and restriction density. Record hardware, heap settings, host version and visible graph size with every result.

Measure first view opening, cold/warm neighbourhood expansion, repeated filtering, label mode changes, layout duration, event-thread stalls and retained heap after repeated clear/reopen cycles. Separate ontology extraction time from rendering and layout time. Establish numerical budgets after the baseline is measured; do not treat synthetic ontology size alone as a usability target.

### Tasks

1. Replace repeated whole-ontology scans with indexes derived from package 3 relationship data.
2. Cache rendered names independently and invalidate them when rendering preferences or relevant annotations change.
3. Apply targeted index invalidation for ontology changes, retaining complete invalidation as a safe fallback.
4. Move expensive extraction into cancellable background work over safe snapshots. Do not assume concurrent OWLAPI or reasoner access is safe.
5. Use request generations so stale worker results cannot replace a newer graph or a different active ontology.
6. Apply scene-graph mutations on Swing's event thread in batches. Keep layout calculations off that thread only where the layout API permits it safely.
7. Add configurable expansion budgets with an explicit indication of omitted neighbours and a way to continue loading.
8. Preserve existing positions and lay out newly added regions when possible. Avoid expensive relationship discovery from paint or style callbacks.
9. Cache text measurements and add simple label collision suppression if profiling shows that dense labels harm usability. Preserve selected-edge visibility priority.

### Completion criteria

Publish before/after measurements for the same workloads. The interface remains responsive during expensive expansion, cancellation works, stale results are ignored, and bounded expansion does not silently imply that all neighbours are shown.

## Package 6 Enrich inspection and navigation

### Tasks

1. Add an edge inspector with the full rendered relationship, supporting axioms, source ontologies and axiom annotations.
2. Provide copy actions for the property IRI and rendered axiom, and navigate to the relevant entity or host axiom view where supported.
3. Distinguish asserted, inferred and domain/range summary relationships with text as well as styling. Enable inferred mode only through an explicit, tested reasoner integration with clear refresh behavior.
4. Add navigation back/forward and pinned node positions. Keep navigation history separate from ontology edit undo/redo.
5. Add optional labels from a selected axiom annotation property, with a language preference, deterministic handling of multiple values and a relationship-text fallback.
6. Provide a legend explaining restriction notation, direction and relationship provenance.

### Completion criteria

A user can identify why an edge exists and inspect all supporting axioms. Pinning survives expansion and layout. Navigation history restores a view without editing the ontology. Label preferences do not affect relationship identity.

## Package 7 Improve persistence and export

### Tasks

1. Parse and validate graph files before changing the visible graph. Keep the existing view intact if reading fails or the file does not match the active ontology.
2. Introduce a versioned structured format storing typed entity IRIs, positions, pinned state, filters, label settings, layout and camera state. Define precedence: saved view settings override global defaults for that view.
3. Handle anonymous ontologies and missing entities explicitly. Preserve compatibility through a legacy reader and provide an actionable report of entries that could not be restored.
4. Write files using explicit UTF-8 and an atomic replacement where supported. Handle malformed input, unsupported versions and cancelled dialogs safely.
5. Generate DOT node IDs from stable typed entity identity; keep human-readable labels separate. Escape quotes, backslashes and line breaks and emit isolated nodes.
6. Export edge direction consistently with the canvas and relationship semantics. Make visible-graph versus full-model scope explicit.
7. Investigate SVG export after the existing image and DOT paths are reliable; include it only if text and geometry can be preserved accurately.

### Completion criteria

New-format save/load restores the intended view, legacy files still load, and malformed files leave the current view untouched. DOT exports preserve distinct entities with identical labels and include isolated nodes and correct directions.

## Package 8 Extend OWL coverage and host support

Deliver each expression family as its own tested change: cardinality restrictions, `hasValue`, data properties and literals, anonymous individuals, then complex expression nodes. Define the visual notation and mapping back to axioms before adding each family. Avoid flattening expressions when doing so loses their meaning.

Evaluate Protégé 6 compatibility on a separate branch against its then-current release and OWLAPI. Decide whether a shared source tree, compatibility module or separate artifact is warranted from actual API differences.

Reassess Cajun after the preceding milestones. Compare maintenance effort, accessibility, label placement, layout quality, memory use and export fidelity against alternatives. Any replacement must preserve the independent relationship model and pass the existing interaction and semantic tests.

## Release gates

| Milestone | Required evidence |
| --- | --- |
| First labelled release | Packages 0, 1 and 2 complete; host compatibility and packaging checks from 4; label/export smoke checks pass |
| Reliable semantic model | Package 3 complete; identity, provenance and incoming/outgoing symmetry tests pass |
| Responsive supported plugin | Packages 4 and 5 complete; reproducible artifact and benchmark report |
| Rich inspection and saved views | Packages 6 and 7 complete; inspector, navigation and persistence tests pass |
| Extended OWL coverage | Each package 8 feature has semantic fixtures, documented notation and host smoke checks |

For each implementation change, record the problem, affected behavior, automated checks, host checks and remaining limitations. Run targeted tests during development and the full agreed suite before a release. Test on supported desktop platforms before claiming cross-platform support.

## Initial implementation backlog

- [x] Record baseline build and dependencies; select the desktop acceptance host.
- [x] Add ontology fixtures and a minimal model test harness.
- [x] Fix cached-set filtering and anonymous individual handling.
- [x] Fix isolated search results and literal search modes.
- [x] Add ontology refresh, cache invalidation and disposal.
- [x] Prove the displayed-arc label extension in the real host — user validated in Protégé Desktop 5.6.9.
- [x] Constrain the Labels dropdown to its preferred content width — user validated in Protégé Desktop 5.6.9.
- [x] Implement label modes, preferences, geometry and image export.
- [ ] Complete first-release smoke checks and user documentation.

After the first release, convert packages 3 through 8 into scoped implementation changes using their completion criteria. Revisit sequencing when the renderer prototype or baseline benchmarks provide evidence that changes the effort or risk.
