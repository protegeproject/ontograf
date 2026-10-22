package org.protege.ontograf.io;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.StringWriter;
import ca.uvic.cs.chisel.cajun.graph.DefaultGraphModel;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
public class DotExporterTest {
    @Test public void exportsIsolatedNodesDistinctIdentitiesAndEscapes() throws Exception {
        DefaultGraphModel model = new DefaultGraphModel();
        GraphNode a = model.addNode(OWLManager.getOWLDataFactory().getOWLClass(IRI.create("urn:a")), "same", null, "class");
        GraphNode b = model.addNode(OWLManager.getOWLDataFactory().getOWLClass(IRI.create("urn:b")), "same", null, "class");
        model.addNode(OWLManager.getOWLDataFactory().getOWLClass(IRI.create("urn:isolated")), "isolated", null, "class");
        model.addArc("edge", a, b, "quote\"slash\\newline\n").setInverted(true);
        StringWriter out = new StringWriter(); DotExporter.write(model, out); String dot = out.toString();
        assertTrue(dot.contains("Class:urn:isolated"));
        assertTrue(dot.contains("\"Class:urn:b\" -> \"Class:urn:a\""));
        assertTrue(dot.contains("quote\\\"slash\\\\newline\\n"));
    }
}
