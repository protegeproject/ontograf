package org.protege.ontograf.model;

import java.util.*;
import org.semanticweb.owlapi.model.*;
import static org.protege.ontograf.model.Relationship.Kind.*;

/** One asserted-axiom projection supplies both incoming and outgoing navigation. */
public final class RelationshipIndex {
    private final Map<Relationship, Relationship> relationships = new LinkedHashMap<>();
    private final Map<OWLEntity, Set<Relationship>> incident = new HashMap<>();
    private final Set<OWLEntity> entities = new HashSet<>();
    private int unsupported;

    public static Map<OWLOntology, Set<OWLAxiom>> snapshot(Collection<OWLOntology> ontologies) {
        Map<OWLOntology, Set<OWLAxiom>> copy = new LinkedHashMap<>();
        for (OWLOntology ontology : ontologies) copy.put(ontology, new LinkedHashSet<>(ontology.getAxioms()));
        return copy;
    }
    public RelationshipIndex(Collection<OWLOntology> ontologies, OWLDataFactory factory) {
        this(snapshot(ontologies), factory);
    }
    public RelationshipIndex(Map<OWLOntology, Set<OWLAxiom>> snapshot, OWLDataFactory factory) {
        Map<OWLObjectProperty, Set<OWLClass>> domains = new HashMap<>(), ranges = new HashMap<>();
        Map<OWLObjectProperty, List<Relationship.Support>> constraints = new HashMap<>();
        for (Map.Entry<OWLOntology, Set<OWLAxiom>> entry : snapshot.entrySet()) {
            OWLOntology ontology = entry.getKey();
            for (OWLAxiom axiom : entry.getValue()) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                entities.addAll(axiom.getSignature());
                if (axiom instanceof OWLSubClassOfAxiom) {
                    OWLSubClassOfAxiom sub = (OWLSubClassOfAxiom) axiom;
                    if (!sub.getSubClass().isAnonymous()) expression(sub.getSubClass().asOWLClass(), sub.getSuperClass(), false, ontology, axiom);
                    else unsupported++;
                } else if (axiom instanceof OWLEquivalentClassesAxiom) {
                    Set<OWLClassExpression> expressions = ((OWLEquivalentClassesAxiom) axiom).getClassExpressions();
                    for (OWLClassExpression source : expressions) if (!source.isAnonymous()) {
                        for (OWLClassExpression target : expressions) if (!source.equals(target)) expression(source.asOWLClass(), target, true, ontology, axiom);
                    }
                } else if (axiom instanceof OWLClassAssertionAxiom) {
                    OWLClassAssertionAxiom assertion = (OWLClassAssertionAxiom)axiom;
                    if (!assertion.getClassExpression().isAnonymous() && assertion.getIndividual().isNamed())
                        add(assertion.getClassExpression().asOWLClass(), assertion.getIndividual().asOWLNamedIndividual(), INSTANCE, null, -1, false, ontology, axiom);
                    else unsupported++;
                } else if (axiom instanceof OWLObjectPropertyAssertionAxiom) {
                    OWLObjectPropertyAssertionAxiom assertion = ((OWLObjectPropertyAssertionAxiom)axiom).getSimplified();
                    if (assertion.getSubject().isNamed() && assertion.getObject().isNamed())
                        add(assertion.getSubject().asOWLNamedIndividual(), assertion.getObject().asOWLNamedIndividual(), ASSERTION, assertion.getProperty(), -1, false, ontology, axiom);
                    else unsupported++;
                } else if (axiom instanceof OWLObjectPropertyDomainAxiom || axiom instanceof OWLObjectPropertyRangeAxiom) {
                    OWLObjectPropertyExpression property;
                    OWLClassExpression expression;
                    boolean domain = axiom instanceof OWLObjectPropertyDomainAxiom;
                    if (domain) {
                        property = ((OWLObjectPropertyDomainAxiom)axiom).getProperty(); expression = ((OWLObjectPropertyDomainAxiom)axiom).getDomain();
                    } else {
                        property = ((OWLObjectPropertyRangeAxiom)axiom).getProperty(); expression = ((OWLObjectPropertyRangeAxiom)axiom).getRange();
                    }
                    if (property.isAnonymous()) domain = !domain;
                    OWLObjectProperty named = property.getNamedProperty();
                    constraints.computeIfAbsent(named, key -> new ArrayList<>()).add(new Relationship.Support(ontology, axiom));
                    Set<OWLClass> endpoints = (domain ? domains : ranges).computeIfAbsent(named, key -> new LinkedHashSet<>());
                    if (!expression.isAnonymous()) endpoints.add(expression.asOWLClass());
                    else unsupported++;
                }
            }
        }
        for (OWLObjectProperty property : constraints.keySet()) {
            Set<OWLClass> from = domains.getOrDefault(property, Collections.singleton(factory.getOWLThing()));
            Set<OWLClass> to = ranges.getOrDefault(property, Collections.singleton(factory.getOWLThing()));
            for (OWLClass source : from) for (OWLClass target : to) {
                for (Relationship.Support support : constraints.get(property)) add(source, target, DOMAIN_RANGE, property, -1, false, support.ontology, support.axiom);
            }
        }
        for (Relationship relationship : relationships.values()) {
            incident.computeIfAbsent(relationship.source, key -> new LinkedHashSet<>()).add(relationship);
            incident.computeIfAbsent(relationship.target, key -> new LinkedHashSet<>()).add(relationship);
        }
    }
    private void expression(OWLClass source, OWLClassExpression expression, boolean equivalent, OWLOntology ontology, OWLAxiom axiom) {
        if (!expression.isAnonymous()) {
            if (equivalent) add(source, expression.asOWLClass(), EQUIVALENT, null, -1, true, ontology, axiom);
            else add(expression.asOWLClass(), source, SUBCLASS, null, -1, false, ontology, axiom);
        } else if (expression instanceof OWLObjectIntersectionOf) {
            for (OWLClassExpression part : expression.asConjunctSet()) {
                // A conjunct of an equivalence is a necessary condition, not individually equivalent.
                if (!part.isAnonymous()) add(part.asOWLClass(), source, SUBCLASS, null, -1, false, ontology, axiom);
                else expression(source, part, equivalent, ontology, axiom);
            }
        } else if (expression instanceof OWLObjectSomeValuesFrom || expression instanceof OWLObjectAllValuesFrom) {
            OWLQuantifiedObjectRestriction restriction = (OWLQuantifiedObjectRestriction)expression;
            if (!restriction.getFiller().isAnonymous()) add(source, restriction.getFiller().asOWLClass(),
                    expression instanceof OWLObjectSomeValuesFrom ? SOME : ALL, restriction.getProperty(), -1, equivalent, ontology, axiom);
            else unsupported++;
        } else if (expression instanceof OWLObjectCardinalityRestriction) {
            OWLObjectCardinalityRestriction restriction = (OWLObjectCardinalityRestriction)expression;
            if (!restriction.getFiller().isAnonymous()) add(source, restriction.getFiller().asOWLClass(),
                    expression instanceof OWLObjectMinCardinality ? MIN : expression instanceof OWLObjectMaxCardinality ? MAX : EXACT,
                    restriction.getProperty(), restriction.getCardinality(), equivalent, ontology, axiom);
            else unsupported++;
        } else if (expression instanceof OWLObjectHasValue) {
            OWLObjectHasValue restriction = (OWLObjectHasValue)expression;
            if (restriction.getValue().isNamed()) add(source, restriction.getValue().asOWLNamedIndividual(), VALUE, restriction.getProperty(), -1, equivalent, ontology, axiom);
            else unsupported++;
        } else unsupported++;
    }
    private void add(OWLEntity source, OWLEntity target, Relationship.Kind kind, OWLObjectPropertyExpression property,
                     int cardinality, boolean equivalent, OWLOntology ontology, OWLAxiom axiom) {
        Relationship key = new Relationship(source, target, kind, property, cardinality, equivalent);
        relationships.computeIfAbsent(key, value -> value).addSupport(ontology, axiom);
    }
    public Set<Relationship> incident(OWLEntity entity) { return Collections.unmodifiableSet(incident.getOrDefault(entity, Collections.emptySet())); }
    public Collection<Relationship> all() { return Collections.unmodifiableCollection(relationships.values()); }
    public Set<OWLEntity> entities() { return Collections.unmodifiableSet(entities); }
    public int unsupportedCount() { return unsupported; }
}
