package org.protege.ontograf.common;

import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import ca.uvic.cs.chisel.cajun.filter.FilterManager;
import ca.uvic.cs.chisel.cajun.graph.arc.GraphArc;
import java.util.*;

public class ProtegeGraphModelTest {
    private ProtegeGraphModel model;
    private OWLOntology ontology;
    private OWLOntologyManager ontologies;
    private OWLDataFactory factory;
    private FilterManager filters;
    @Before public void setup() throws Exception {
        ontologies = OWLManager.createOWLOntologyManager(); factory = ontologies.getOWLDataFactory();
        ontology = ontologies.loadOntologyFromOntologyDocument(getClass().getResourceAsStream("/relationships.ofn"));
        OWLModelManager manager = mock(OWLModelManager.class);
        when(manager.getActiveOntologies()).thenReturn(Collections.singleton(ontology));
        when(manager.getOWLDataFactory()).thenReturn(factory);
        when(manager.getRendering(any(OWLObject.class))).thenAnswer(invocation -> invocation.getArgument(0).toString());
        OWLEditorKit kit = mock(OWLEditorKit.class); when(kit.getModelManager()).thenReturn(manager);
        model = new ProtegeGraphModel(kit); filters = mock(FilterManager.class);
        when(filters.isArcTypeVisible(any())).thenReturn(true);
    }
    private OWLNamedIndividual individual(String name) { return factory.getOWLNamedIndividual(IRI.create("urn:ontograf:test#" + name)); }
    @Test public void filteringDoesNotDestroyCachedRelationships() {
        OWLNamedIndividual alice = individual("alice");
        model.show(alice, filters); model.expandNode(model.getNode(alice));
        int count = model.getAllArcs().size(); assertTrue(count > 0);
        when(filters.isArcTypeVisible(any())).thenReturn(false);
        model.showNeighborhood(alice, true); assertEquals(0, model.getAllArcs().size());
        when(filters.isArcTypeVisible(any())).thenReturn(true);
        model.showNeighborhood(alice, true); assertEquals(count, model.getAllArcs().size());
    }
    @Test public void isolatedRootsSurviveAndRemovedAxiomsRefresh() {
        OWLClass isolated = factory.getOWLClass(IRI.create("urn:ontograf:test#Isolated"));
        model.show(isolated, filters); model.showNeighborhood(isolated, true); assertNotNull(model.getNode(isolated));
        model.show(individual("alice"), filters); model.expandNode(model.getNode(individual("alice")));
        int before = model.getAllArcs().size();
        ontologies.removeAxioms(ontology, ontology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION));
        model.refreshVisible(); assertTrue(model.getAllArcs().size() < before);
        assertNotNull(model.getNode(isolated));
    }
    @Test public void limitCanBeContinuedAndPinnedNodesSurviveReplacement() {
        model.setExpansionLimit(1); model.show(individual("alice"), filters);
        model.expandNode(model.getNode(individual("alice"))); assertTrue(model.getOmittedCount() > 0);
        int before = model.getAllArcs().size(); model.expandNode(model.getNode(individual("alice")));
        assertTrue(model.getAllArcs().size() > before);
        model.setPinned(individual("alice"), true);
        model.showNeighborhood(factory.getOWLClass(IRI.create("urn:ontograf:test#Isolated")), true);
        assertNotNull(model.getNode(individual("alice")));
    }
    @Test public void axiomAnnotationOverridesRelationshipLabelWithFallback() throws Exception {
        OWLClass person = factory.getOWLClass(IRI.create("urn:ontograf:test#Person"));
        model.show(person, filters); model.expandNode(model.getNode(person));
        model.setLabelAnnotation(IRI.create("http://www.w3.org/2000/01/rdf-schema#label"), "en");
        boolean annotated = false, fallback = false;
        for (GraphArc arc : model.getAllArcs()) {
            String label = ((edu.umd.cs.piccolo.nodes.PText)((org.protege.ontograf.ui.LabelledGraphArc)arc).getChild(0)).getText();
            if (label.trim().equals("employment")) annotated = true; else fallback = true;
        }
        assertTrue(annotated); assertTrue(fallback);
        java.io.StringWriter dot = new java.io.StringWriter();
        org.protege.ontograf.io.DotExporter.write(model, dot);
        assertTrue(dot.toString().contains("employment"));
    }
}
