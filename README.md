OntoGraf
====================

Protege Desktop plug-in that allows visual, interactive navigation of the relationships in OWL ontologies.

OntoGraf is bundled with the default installation of Protege Desktop.  There are no additional installation steps required.  Enable OntoGraf from the Window | Tabs menu.  More user documentation is available on the Protege wiki:

http://protegewiki.stanford.edu/wiki/OntoGraf

## Protégé 6 migration

OntoGraf 3 targets Protégé 6.0.0-alpha-1, OWL API 5.5.1, and Java 11. It retains the Cajun graph library and aligns its provided Guava dependency with the Protégé runtime. Protégé 5 requires the OntoGraf 2 release line.

Build and run the regression tests with:

```sh
mvn --batch-mode clean verify
```

For application testing, copy `target/ontograf-3.0.0.jar` into the `plugins` directory of an assembled Protégé 6 distribution, removing any older OntoGraf JAR first. Enable **Window > Tabs > OntoGraf** and select the tab.

Open `src/test/resources/ontograf-smoke-test.ofn` and exercise:

- Search for `Person`, `Pet`, and `Alice`; expand and collapse their neighborhoods.
- Display `owns` domain/range arcs, existential restrictions, and individual assertions.
- Toggle relationship filters and inspect class and individual tooltips.
- Select graph nodes and check that Protégé's global selection follows.
- Classify with HermiT, then check the inferred class hierarchy (`Dog` under `Pet`) and re-expand affected nodes.
- Edit a restriction and search/expand again to check the updated graph.
- Open an ontology with imports and check relationships across its imports closure.

Migration tracking: https://github.com/protegeproject/protege/issues/1361. Interactive verification and release integration must finish before this issue is closed.
