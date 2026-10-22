# OntoGraf

A Protégé Desktop plugin for interactive navigation of OWL ontology relationships.
Enable it from **Window → Tabs → OntoGraf**.

## Relationship labels

Use **Labels: Off / Selected / All** in the toolbar. Choose **All** to keep labels
visible when clicking empty canvas or changing selection. **Selected** shows labels
only on selected edges or edges incident to selected nodes: clicking empty canvas
clears selection and hides them. **Off** hides all labels. The preference is
remembered across sessions.
Labels follow dragging and layouts, support curved edges and self-loops, and are
included in image export. Long labels are shortened; full descriptions remain in
tooltips and the edge inspector. Labels hide below 45% zoom.

Property names come from **Protégé's configured entity renderer**. Configure that
renderer to prefer `rdfs:label` if you want those labels. Subclass and membership
edges use fixed phrases; restrictions add `some`, `all`, `min`, `max`, `exact` or
`value`. Two properties with the same label remain distinct edges; filter entries
also show the property IRI to disambiguate them.

**Graph options → Label source** optionally selects an annotation on the supporting
axiom, rather than an annotation on the property. Enter an annotation property IRI
and preferred language. The default is relationship rendering. If an axiom has no
matching annotation, its edge falls back to relationship rendering. Multiple
values are chosen deterministically: preferred language, untagged, then another
language, with lexical ordering. Saved views retain this choice.

## Navigation and inspection

Double-click an edge to inspect its supporting axioms, ontology provenance and
annotations. Text in the inspector can be selected and copied. Double-click nodes
to expand/collapse. Use **Graph options** to pin selected nodes, set an expansion
budget, read the legend, refresh the ontology index or cancel a refresh.

Expansion defaults to 200 new edges per operation. When more edges remain, the
status bar reports the count; expand again to continue. Back/Forward restores
view state independently of ontology edit undo/redo.

The graph currently projects **asserted axioms**. Domain/range edges summarise
constraints and do not assert relationships between instances. Supported
projections include named subclass relationships, class assertions, object-property
assertions, named equivalences, intersections of supported conditions, some/all
restrictions, named-filler object cardinality restrictions and object `hasValue`.
Anonymous individuals, data/literal nodes and nested anonymous fillers are not yet
visualised. Unsupported expressions are not reinterpreted as another restriction.

## Saved views and exports

New `.graph` files use a versioned UTF-8 format with typed entity IRIs, positions,
pins, camera transform, layout, filters and label settings. Legacy files remain
readable. Named ontology identity includes its version; anonymous ontologies are
matched by a digest of their entity signature. Missing entities are reported.
Saved view settings override defaults for the restored view.

DOT exports the visible graph with stable entity identifiers, escaped labels,
correct edge directions and isolated nodes. Image exports reflect canvas labels
and current visibility.

## Build and installation

Use JDK 11 or newer and Maven 3.9.x:

```sh
mvn --batch-mode verify
```

The candidate jar is `target/ontograf-2.0.4-SNAPSHOT.jar`. It compiles against
Protégé editor API 5.6.6 and OWLAPI 4.5.29, targeting the Protégé 5.6.x desktop
line. The plugin, displayed axiom labels and compact Labels dropdown have been
validated by the user in Protégé Desktop 5.6.9. Broader release checks remain in
the validation checklist. This is not a Protégé 6 plugin. In a disposable host installation, replace its bundled OntoGraf
jar with the candidate rather than installing two copies.

See [the execution plan](IMPLEMENTATION_PLAN.md) for delivery status and
[validation instructions](docs/VALIDATION.md) for baseline evidence, tests,
benchmarks, host smoke checks and remaining release work.
