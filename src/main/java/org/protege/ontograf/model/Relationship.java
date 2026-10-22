package org.protege.ontograf.model;

import java.util.*;
import java.util.function.Function;
import org.semanticweb.owlapi.model.*;

/** Semantic edge identity never includes rendered labels or axiom annotations. */
public final class Relationship {
    public enum Kind { SUBCLASS, INSTANCE, SOME, ALL, MIN, MAX, EXACT, VALUE, EQUIVALENT, DOMAIN_RANGE, ASSERTION }
    public final OWLEntity source;
    public final OWLEntity target;
    public final Kind kind;
    public final OWLObjectPropertyExpression property;
    public final int cardinality;
    public final boolean equivalent;
    private final Set<Support> support = new LinkedHashSet<>();

    public Relationship(OWLEntity source, OWLEntity target, Kind kind,
                        OWLObjectPropertyExpression property, int cardinality, boolean equivalent) {
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        this.kind = kind; this.property = property; this.cardinality = cardinality; this.equivalent = equivalent;
    }
    void addSupport(OWLOntology ontology, OWLAxiom axiom) { support.add(new Support(ontology, axiom)); }
    public Set<Support> getSupport() { return Collections.unmodifiableSet(support); }
    public boolean isHierarchy() { return kind == Kind.SUBCLASS || kind == Kind.INSTANCE; }
    public String label(Function<OWLObject, String> render) {
        return formatLabel(kind, property, cardinality, equivalent, render);
    }
    private static String formatLabel(Kind kind, OWLObjectPropertyExpression property, int cardinality,
                                      boolean equivalent, Function<OWLObject, String> render) {
        switch (kind) {
            case SUBCLASS: return "has subclass";
            case INSTANCE: return "has individual";
            case EQUIVALENT: return "equivalent to";
            default:
                String name = render.apply(property);
                if (kind == Kind.ASSERTION) return name;
                if (kind == Kind.DOMAIN_RANGE) return name + " (Domain>Range summary)";
                String suffix = kind.name().toLowerCase(Locale.ROOT);
                if (kind == Kind.MIN || kind == Kind.MAX || kind == Kind.EXACT) suffix += " " + cardinality;
                return name + " (" + (equivalent ? "Equivalent class " : "Subclass ") + suffix + ")";
        }
    }
    public Object type(Function<OWLObject, String> render) {
        if (isHierarchy()) return label(render);
        return new Type(kind, property, cardinality, equivalent, render);
    }
    @Override public boolean equals(Object value) {
        if (!(value instanceof Relationship)) return false;
        Relationship other = (Relationship) value;
        return source.equals(other.source) && target.equals(other.target) && kind == other.kind
                && Objects.equals(property, other.property) && cardinality == other.cardinality && equivalent == other.equivalent;
    }
    @Override public int hashCode() { return Objects.hash(source, target, kind, property, cardinality, equivalent); }

    public static final class Support {
        public final OWLOntology ontology;
        public final OWLAxiom axiom;
        Support(OWLOntology ontology, OWLAxiom axiom) { this.ontology = ontology; this.axiom = axiom; }
        @Override public boolean equals(Object value) {
            return value instanceof Support && ontology.equals(((Support)value).ontology) && axiom.equals(((Support)value).axiom);
        }
        @Override public int hashCode() { return Objects.hash(ontology, axiom); }
    }
    public static final class Type {
        public final Kind kind;
        private final OWLObjectPropertyExpression property;
        private final int cardinality;
        private final boolean equivalent;
        private final Function<OWLObject, String> renderer;
        public String getLabel() { return formatLabel(kind, property, cardinality, equivalent, renderer); }
        Type(Kind kind, OWLObjectPropertyExpression property, int cardinality, boolean equivalent, Function<OWLObject, String> renderer) {
            this.kind = kind; this.property = property; this.cardinality = cardinality; this.equivalent = equivalent; this.renderer = renderer;
        }
        public String storageKey() { return kind.name() + "|" + property + "|" + cardinality + "|" + equivalent; }
        public boolean isEquivalent() { return equivalent || kind == Kind.EQUIVALENT; }
        @Override public String toString() { return getLabel() + (property == null ? "" : " <" + property.getNamedProperty().getIRI() + ">"); }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Type)) return false;
            Type other = (Type)value;
            return kind == other.kind && Objects.equals(property, other.property) && cardinality == other.cardinality && equivalent == other.equivalent;
        }
        @Override public int hashCode() { return Objects.hash(kind, property, cardinality, equivalent); }
    }
}
