package org.protege.ontograf.common;

import ca.uvic.cs.chisel.cajun.filter.FilterManager;
import ca.uvic.cs.chisel.cajun.graph.arc.GraphArc;
import ca.uvic.cs.chisel.cajun.graph.AbstractGraph;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.nodes.PText;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.ontograf.common.util.OWLIconProviderImpl;
import org.protege.ontograf.ui.FrameTooltipNode;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ProtegeGraphModelTest {
    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory df;
    private OWLClass source, target;
    private OWLObjectProperty property;
    private OWLNamedIndividual alice, bob;
    private OWLModelManager modelManager;
    private ProtegeGraphModel model;
    private FilterManager filters;

    @Before
    public void setUp() throws Exception {
        manager = OWLManager.createOWLOntologyManager();
        df = manager.getOWLDataFactory();
        ontology = manager.createOntology(IRI.create("urn:ontograf:test"));
        source = df.getOWLClass(IRI.create("urn:ontograf:Source"));
        target = df.getOWLClass(IRI.create("urn:ontograf:Target"));
        property = df.getOWLObjectProperty(IRI.create("urn:ontograf:relatedTo"));
        alice = df.getOWLNamedIndividual(IRI.create("urn:ontograf:alice"));
        bob = df.getOWLNamedIndividual(IRI.create("urn:ontograf:bob"));
        OWLEditorKit kit = mock(OWLEditorKit.class, RETURNS_DEEP_STUBS);
        modelManager = kit.getModelManager();
        when(kit.getOWLModelManager()).thenReturn(modelManager);
        when(modelManager.getActiveOntologies()).thenReturn(new LinkedHashSet<>(Collections.singleton(ontology)));
        when(modelManager.getActiveOntology()).thenReturn(ontology);
        when(modelManager.getRendering(any(OWLObject.class))).thenAnswer(call -> call.getArgument(0).toString());
        when(modelManager.getOWLEntityFinder().getOWLClass("Thing")).thenReturn(df.getOWLThing());
        filters = mock(FilterManager.class);
        when(filters.isArcTypeVisible(any())).thenReturn(true);
        model = new ProtegeGraphModel(kit);
        // Initialize the normal search/filter path without adding graph nodes.
        model.search("no match", filters);
    }

    @Test
    public void expandsSubclassAndEquivalentRestrictions() {
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(source, df.getOWLObjectSomeValuesFrom(property, target)));
        manager.addAxiom(ontology, df.getOWLEquivalentClassesAxiom(source, df.getOWLObjectAllValuesFrom(property, target)));
        Set<GraphArc> outgoing = model.createOutgoingRelationships(source, false);
        assertArc(outgoing, source, target, "(Subclass some)");
        assertArc(outgoing, source, target, "(Equivalent class all)");
        assertArc(model.createIncomingRelationships(target, false), source, target, "(Subclass some)");
    }

    @Test
    public void expandsClassMembershipInBothDirections() {
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(source, alice));
        assertArc(model.createOutgoingRelationships(source, false), source, alice, "has individual");
        assertArc(model.createIncomingRelationships(alice, false), source, alice, "has individual");
    }

    @Test
    public void expandsObjectAssertionsInBothDirections() {
        manager.addAxiom(ontology, df.getOWLObjectPropertyAssertionAxiom(property, alice, bob));
        assertArc(model.getCachedArcsForEntity(alice), alice, bob, "relatedTo");
        assertArc(model.createIncomingRelationships(bob, false), alice, bob, "relatedTo");
    }

    @Test
    public void expandsImportedRestrictions() throws Exception {
        OWLOntology imported = manager.createOntology(IRI.create("urn:ontograf:imported"));
        manager.applyChange(new AddImport(ontology, df.getOWLImportsDeclaration(imported.getOntologyID().getOntologyIRI().get())));
        manager.addAxiom(imported, df.getOWLSubClassOfAxiom(source, df.getOWLObjectSomeValuesFrom(property, target)));
        // Incoming references must traverse the imports closure.
        assertArc(model.createIncomingRelationships(target, false), source, target, "(Subclass some)");
    }

    @Test
    public void createsDomainRangeArcsAndDefaultsMissingRangeToThing() {
        manager.addAxiom(ontology, df.getOWLObjectPropertyDomainAxiom(property, source));
        assertArc(model.createOutgoingRelationships(source, false), source, df.getOWLThing(), "(Domain>Range)");
    }

    @Test
    public void honorsRelationshipFilters() {
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(source, df.getOWLObjectSomeValuesFrom(property, target)));
        when(filters.isArcTypeVisible(contains("(Subclass some)"))).thenReturn(false);
        assertFalse(model.createOutgoingRelationships(source, false).stream()
                .anyMatch(arc -> arc.getType().toString().contains("(Subclass some)")));
    }

    @Test
    public void usesHostHierarchyProviderForInferredRelationships() {
        when(modelManager.getOWLHierarchyManager().getOWLClassHierarchyProvider().getChildren(source))
                .thenReturn(Collections.singleton(target));
        assertArc(model.createOutgoingRelationships(source, false), source, target, "has subclass");
    }

    @Test
    public void seesRestrictionChangesOnSubsequentExpansion() {
        OWLAxiom axiom = df.getOWLSubClassOfAxiom(source, df.getOWLObjectSomeValuesFrom(property, target));
        manager.addAxiom(ontology, axiom);
        assertArc(model.createOutgoingRelationships(source, false), source, target, "(Subclass some)");
        manager.removeAxiom(ontology, axiom);
        assertTrue(model.createOutgoingRelationships(source, false).isEmpty());
    }

    @Test
    public void classTooltipIncludesAnnotationsFromStreamSearches() {
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), source.getIRI(),
                df.getOWLLiteral("Class annotation")));
        assertTrue(tooltipText(source).contains("Class annotation"));
    }

    @Test
    public void individualTooltipIncludesIdentityAnnotationsAndNegativeAssertions() {
        manager.addAxiom(ontology, df.getOWLSameIndividualAxiom(alice, bob));
        manager.addAxiom(ontology, df.getOWLDifferentIndividualsAxiom(alice,
                df.getOWLNamedIndividual(IRI.create("urn:ontograf:charlie"))));
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), alice.getIRI(),
                df.getOWLLiteral("Individual annotation")));
        manager.addAxiom(ontology, df.getOWLNegativeObjectPropertyAssertionAxiom(property, alice, bob));
        manager.addAxiom(ontology, df.getOWLNegativeDataPropertyAssertionAxiom(
                df.getOWLDataProperty(IRI.create("urn:ontograf:age")), alice, df.getOWLLiteral(21)));
        String text = tooltipText(alice);
        assertTrue(text.contains("Same individuals:"));
        assertTrue(text.contains("Different individuals:"));
        assertTrue(text.contains("Individual annotation"));
        assertTrue(text.contains("Negative object property assertions:"));
        assertTrue(text.contains("Negative data property assertions:"));
    }

    @Test
    public void iconVisitorDistinguishesDefinedClassesAndResetsBetweenObjects() {
        OWLIconProviderImpl icons = new OWLIconProviderImpl(modelManager);
        Object primitiveIcon = icons.getIcon(source);
        assertNotNull(primitiveIcon);
        manager.addAxiom(ontology, df.getOWLEquivalentClassesAxiom(source,
                df.getOWLObjectSomeValuesFrom(property, target)));
        Object definedIcon = icons.getIcon(source);
        assertNotNull(definedIcon);
        assertNotSame(primitiveIcon, definedIcon);
        assertNotNull(icons.getIcon(property));
        assertSame(primitiveIcon, icons.getIcon(target));
    }

    private String tooltipText(OWLEntity entity) {
        AbstractGraph graph = mock(AbstractGraph.class);
        PCanvas canvas = new PCanvas();
        canvas.setSize(800, 600);
        when(graph.getCanvas()).thenReturn(canvas);
        when(graph.getCamera()).thenReturn(canvas.getCamera());
        when(graph.getWidth()).thenReturn(800);
        PNode owner = new PNode();
        owner.setBounds(0, 0, 100, 30);
        FrameTooltipNode tooltip = new FrameTooltipNode(modelManager, graph, owner, entity);
        StringBuilder text = new StringBuilder();
        for (Object child : tooltip.getChildrenReference()) {
            if (child instanceof PText) {
                text.append(((PText) child).getText()).append('\n');
            }
        }
        return text.toString();
    }

    private void assertArc(Set<GraphArc> arcs, OWLEntity from, OWLEntity to, String type) {
        assertTrue("Missing " + from + " -> " + to + " (" + type + ")", arcs.stream().anyMatch(arc ->
                from.equals(arc.getSource().getUserObject()) && to.equals(arc.getDestination().getUserObject())
                        && arc.getType().toString().contains(type)));
    }
}
