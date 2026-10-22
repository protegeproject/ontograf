# OntoGraf validation and release checklist

## Build baseline

The starting source revision is `866cc3a4ed2a4aee45e638bbb636bf688d196d5e`.
The original package build passed with Maven 3.9.12 and Zulu JDK 21.0.12.1;
there were no tests. The original jar and manifest were retained under
`target/baseline` during implementation. Generated build files are not committed.

The candidate builds against the published Protégé editor API 5.6.6 and its
OWLAPI 4.5.29 dependency, with Java 11 bytecode. The user has validated plugin
operation, displayed axiom labels and the compact Labels dropdown in Protégé
Desktop 5.6.9. There is no 5.6.9 editor API artifact in Maven Central;
the desktop distribution version and Maven API version are different here.

## Automated checks

Run `mvn --batch-mode verify`. CI runs this command on Java 11 and Java 21.
The suite contains 22 tests and passes locally on Java 11 and Java 21.
Tests exercise real OWLAPI fixtures, relationship identity and provenance,
incoming/outgoing lookup symmetry, inverse assertions, cardinality and value
restrictions, anonymous-individual omission, cache-safe filtering, isolated
roots, ontology refresh, expansion continuation, pinning, annotation label
fallback, literal search, label geometry and offscreen image pixels, graph-file
round trips and malformed files, escaped DOT output with isolated nodes, and a
headless Cajun controller round trip with failed-load preservation and listener
unregistration.

Inspect `target/surefire-reports` for results and
`target/relationship-benchmark.txt` for the repeatable lookup microbenchmark.
`target/ontograf-2.0.4-SNAPSHOT.jar` is the candidate plugin artifact.

The generated bundle embeds Cajun only, leaves host APIs provided, has no
borrowed Protégé activator and does not export plugin implementation packages.
Inspect `META-INF/MANIFEST.MF` in the candidate jar before release. Successful
user testing confirms loading in Protégé 5.6.9; a detailed host-log review remains
part of the broader release checks.

## Performance measurement

The synthetic benchmark constructs 5,000 subclass edges and checks that 1,000
incident-edge lookups return the same results by full scan and by index.
One local Java 11 run measured 54.7 ms to construct the index, 627.8 ms for
repeated scans and 0.56 ms for indexed lookup. These are algorithm-level
measurements, not an end-to-end comparison with the original plugin. The test
records runtime, processor count and heap limit with every run and imposes no
flaky timing assertion.

Still measure cold/warm view opening, real ontology extraction, layout,
filtering, text rendering, event-thread stalls and retained memory on a desktop
host. Full invalidation remains the safe refresh strategy. Snapshot copying and
first-use synchronous index fallback can still block the event thread; layout
also still uses Cajun's execution behavior. Background refresh is cancellable,
and generation checks prevent outdated results replacing newer state.

## Desktop smoke test

Use a disposable Protégé 5.6.9 installation. Back up its plugin directory,
remove or move aside its bundled OntoGraf jar, and install only the candidate
jar. Do not load both OntoGraf versions together. Start the desktop on a
supported Java runtime and check the application log for bundle resolution and
class-loading errors.

1. Open `src/test/resources/relationships.ofn` and the OntoGraf tab. Configure
   Protégé's entity renderer to prefer `rdfs:label`; verify the two properties
   labelled “works for” remain separate relationships and filter entries.
2. Select an entity and compare Off, Selected and All label modes. Drag nodes,
   zoom in/out, exercise every layout, and inspect self-loops and parallel edges.
3. Change a property's label, add/remove assertions, and switch active ontologies.
   Check that names and edges refresh and selection/positions remain meaningful.
4. Hide and restore relationships. Search for Isolated and for literal regexp
   metacharacters. An invalid regexp should produce a useful message.
5. Double-click an edge and inspect its supporting axioms. Try an axiom annotation
   label with the `rdfs:label` IRI and preferred language `en`; “employment” should
   appear on its annotated restriction edge, with fallback on other edges.
6. Pin selected nodes, expand with a small budget, expand again, and use navigation
   Back/Forward. Check that an omitted-edge count is visible and no ontology edits
   are produced by graph navigation.
7. Save/open a view with filters, camera position, labels and pinned nodes. Try a
   malformed file, a different ontology and a file with missing entities; failures
   must leave the current graph intact when nothing can be restored.
8. Export PNG and DOT. Verify labels, identities, isolated nodes, visible filter
   scope and direction. Parse DOT with Graphviz if available.
9. Open an ontology with cyclic imports in the imports view. Reopen/close both
   views repeatedly and inspect listener counts, pending workers and heap retention.
10. Repeat label and menu checks on high DPI displays and supported host themes.
    Run on each desktop platform before claiming cross-platform support.

The user confirmed successful desktop testing in Protégé 5.6.9, including axiom
labels and the dropdown width fix. This does not imply that every item in the
checklist above has been exercised. No graphical display or Xvfb is available in
the implementation environment; remaining interactive checks require a desktop
host. Offscreen rendering tests do not substitute for these checks.

## Remaining release work

- Complete desktop smoke tests and address any host or UI failures before release.
- Test cancellation and refresh races with a running editor; measure memory and
  layout latency using representative user ontologies.
- Add targeted invalidation only after measuring full refresh cost.
- Add explicit inferred relationships only with a tested reasoner integration.
- Extend node types for literals, anonymous individuals and nested expressions
  before adding data-property and complex-expression visualisations.
- Evaluate Protégé 6 and OWLAPI 5 in a separate compatibility change. The candidate
  intentionally requires OWLAPI 4 packages and is not a Protégé 6 artifact.
- Investigate vector export and alternative renderers after interactive evidence.
