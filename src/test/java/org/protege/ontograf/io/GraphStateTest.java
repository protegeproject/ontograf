package org.protege.ontograf.io;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class GraphStateTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void roundTripsSettingsAndUnicode() throws Exception {
        GraphState state = new GraphState(); state.ontology = "OntologyID(urn:example)"; state.labelMode = "ALL";
        state.nodes.add(new GraphState.Node("Class", "urn:é,comma", 10.5, -2.3, true));
        state.filters.put("SOME|<urn:property>|-1|false", false);
        state.nodeFilters.put("individual", false);
        state.annotation = "http://www.w3.org/2000/01/rdf-schema#label"; state.language = "fr";
        Path path = temporary.newFile().toPath(); state.write(path); GraphState read = GraphState.read(path);
        assertEquals(state.ontology, read.ontology); assertEquals("ALL", read.labelMode);
        assertEquals("urn:é,comma", read.nodes.get(0).iri); assertTrue(read.nodes.get(0).pinned);
        assertEquals(state.filters, read.filters); assertEquals(state.nodeFilters, read.nodeFilters);
        assertEquals(state.annotation, read.annotation); assertEquals("fr", read.language);
    }
    @Test public void readsLegacyRowsWithCommasInIri() throws Exception {
        Path path = temporary.newFile().toPath();
        Files.write(path, "Optional.of(urn:test)\nurn:entity,part,1.0,2.0\n".getBytes(StandardCharsets.UTF_8));
        GraphState state = GraphState.read(path); assertTrue(state.legacy); assertEquals("urn:entity,part", state.nodes.get(0).iri);
    }
    @Test(expected=java.io.IOException.class) public void rejectsNonFiniteCoordinates() throws Exception {
        Path path = temporary.newFile().toPath(); Files.write(path, "urn:test\nurn:entity,NaN,2\n".getBytes(StandardCharsets.UTF_8)); GraphState.read(path);
    }
    @Test(expected=java.io.IOException.class) public void rejectsUnknownVersions() throws Exception {
        Path path = temporary.newFile().toPath(); Files.write(path, "# OntoGraf view\nversion=99\n".getBytes(StandardCharsets.UTF_8)); GraphState.read(path);
    }
}
