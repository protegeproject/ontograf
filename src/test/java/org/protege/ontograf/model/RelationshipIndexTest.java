package org.protege.ontograf.model;

import org.junit.*;
import static org.junit.Assert.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.util.*;

public class RelationshipIndexTest {
    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;
    private RelationshipIndex index;
    @Before public void load() throws Exception {
        manager = OWLManager.createOWLOntologyManager(); factory = manager.getOWLDataFactory();
        ontology = manager.loadOntologyFromOntologyDocument(getClass().getResourceAsStream("/relationships.ofn"));
        index = new RelationshipIndex(Collections.singleton(ontology), factory);
    }
    @Test public void fixtureSupportsBothDirectionsAndRestrictions() {
        EnumSet<Relationship.Kind> kinds = EnumSet.noneOf(Relationship.Kind.class);
        for (Relationship edge : index.all()) {
            kinds.add(edge.kind);
            assertTrue(index.incident(edge.source).contains(edge));
            assertTrue(index.incident(edge.target).contains(edge));
            assertFalse(edge.getSupport().isEmpty());
        }
        assertTrue(kinds.containsAll(Arrays.asList(Relationship.Kind.SUBCLASS, Relationship.Kind.INSTANCE,
                Relationship.Kind.SOME, Relationship.Kind.ALL, Relationship.Kind.MIN, Relationship.Kind.VALUE,
                Relationship.Kind.DOMAIN_RANGE, Relationship.Kind.ASSERTION)));
        assertTrue(index.unsupportedCount() > 0);
    }
    @Test public void duplicateLabelsDoNotMergeProperties() {
        OWLNamedIndividual alice = factory.getOWLNamedIndividual(IRI.create("urn:ontograf:test#alice"));
        OWLNamedIndividual acme = factory.getOWLNamedIndividual(IRI.create("urn:ontograf:test#acme"));
        List<Relationship> edges = new ArrayList<>();
        for (Relationship edge : index.incident(alice)) if (edge.kind == Relationship.Kind.ASSERTION && edge.target.equals(acme)) edges.add(edge);
        assertEquals(2, edges.size());
        assertEquals(edges.get(0).label(object -> "same label"), edges.get(1).label(object -> "same label"));
        assertNotEquals(edges.get(0), edges.get(1));
        assertNotEquals(edges.get(0).type(object -> "same label"), edges.get(1).type(object -> "same label"));
    }
    @Test public void provenanceAggregatesAcrossOntologies() throws Exception {
        OWLOntology imported = manager.createOntology(IRI.create("urn:imported"));
        OWLAxiom axiom = ontology.getAxioms(AxiomType.SUBCLASS_OF).iterator().next();
        manager.addAxiom(imported, axiom);
        index = new RelationshipIndex(Arrays.asList(ontology, imported), factory);
        assertTrue(index.all().stream().anyMatch(edge -> edge.getSupport().size() == 2));
    }
    @Test public void labelChangesDoNotChangeIdentity() {
        Relationship edge = index.all().iterator().next();
        int hash = edge.hashCode();
        edge.label(object -> "new rendering");
        assertEquals(hash, edge.hashCode());
        assertEquals(edge.type(object -> "old"), edge.type(object -> "new"));
    }
    @Test public void punningRemainsDistinctAndInverseAssertionsNormalize() throws Exception {
        OWLClass type = factory.getOWLClass(IRI.create("urn:same"));
        OWLNamedIndividual individual = factory.getOWLNamedIndividual(type.getIRI());
        Relationship a = new Relationship(type, type, Relationship.Kind.EQUIVALENT, null, -1, false);
        Relationship b = new Relationship(individual, individual, Relationship.Kind.EQUIVALENT, null, -1, false);
        assertNotEquals(a, b);
        OWLObjectProperty p = factory.getOWLObjectProperty(IRI.create("urn:p"));
        OWLNamedIndividual target = factory.getOWLNamedIndividual(IRI.create("urn:target"));
        manager.addAxiom(ontology, factory.getOWLObjectPropertyAssertionAxiom(p.getInverseProperty(), individual, target));
        index = new RelationshipIndex(Collections.singleton(ontology), factory);
        assertTrue(index.all().stream().anyMatch(edge -> edge.kind == Relationship.Kind.ASSERTION && edge.source.equals(target) && edge.target.equals(individual)));
    }
}
