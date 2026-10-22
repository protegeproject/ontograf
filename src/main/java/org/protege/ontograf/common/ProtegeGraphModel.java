package org.protege.ontograf.common;

import java.util.*;
import java.util.regex.Pattern;
import javax.swing.Icon;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.ontograf.common.util.OWLIconProviderImpl;
import org.protege.ontograf.model.Relationship;
import org.protege.ontograf.model.RelationshipIndex;
import org.protege.ontograf.ui.EdgeLabelMode;
import org.protege.ontograf.ui.LabelledGraphArc;
import org.semanticweb.owlapi.model.*;
import ca.uvic.cs.chisel.cajun.filter.FilterManager;
import ca.uvic.cs.chisel.cajun.graph.DefaultGraphModel;
import ca.uvic.cs.chisel.cajun.graph.arc.*;
import ca.uvic.cs.chisel.cajun.graph.node.*;

/** Adapts an asserted OWL relationship index to the visible Cajun graph. */
public class ProtegeGraphModel extends DefaultGraphModel {
    public static final String DIRECT_SUBCLASS_SLOT_TYPE = "has subclass";
    public static final String DIRECT_INDIVIDUAL_SLOT_TYPE = "has individual";
    private final OWLModelManager manager;
    private final OWLIconProviderImpl icons;
    private RelationshipIndex index;
    private Collection<Object> arcTypes;
    private final Map<OWLObject, String> renderings = new HashMap<>();
    private String render(OWLObject object) { return renderings.computeIfAbsent(object, manager::getRendering); }
    private FilterManager filterManager;
    private EdgeLabelMode labelMode = EdgeLabelMode.SELECTED;
    protected Object restrictToArcType;
    private final Set<OWLEntity> pinned = new HashSet<>();
    private int expansionLimit = 200;
    private int omitted;
    private IRI labelAnnotation;
    private String labelLanguage = "en";

    public ProtegeGraphModel(OWLEditorKit kit) {
        manager = kit.getModelManager();
        icons = new OWLIconProviderImpl(manager);
    }
    public OWLModelManager getOwlModelManager() { return manager; }
    public RelationshipIndex getRelationshipIndex() {
        if (index == null) { index = new RelationshipIndex(new HashSet<>(manager.getActiveOntologies()), manager.getOWLDataFactory()); arcTypes = null; }
        return index;
    }
    public void invalidateRelationships() { index = null; arcTypes = null; renderings.clear(); restrictToArcType = null; }
    public void installIndex(RelationshipIndex replacement) { index = Objects.requireNonNull(replacement); arcTypes = null; renderings.clear(); }
    public void setExpansionLimit(int limit) { if (limit < 1) throw new IllegalArgumentException("Limit must be positive"); expansionLimit = limit; }
    public int getExpansionLimit() { return expansionLimit; }
    public int getOmittedCount() { return omitted; }
    public Set<OWLEntity> getPinnedEntities() { return Collections.unmodifiableSet(pinned); }
    public void setPinned(OWLEntity entity, boolean value) {
        if (value) pinned.add(entity); else pinned.remove(entity);
        GraphNode node = getNode(entity);
        if (node != null) node.setFixedLocation(value);
    }
    public void setLabelMode(EdgeLabelMode mode) {
        labelMode = Objects.requireNonNull(mode);
        for (GraphArc arc : getAllArcs()) ((DefaultGraphArc)arc).invalidatePaint();
    }
    public EdgeLabelMode getLabelMode() { return labelMode; }
    public void setLabelAnnotation(IRI property, String language) {
        labelAnnotation = property; labelLanguage = language == null ? "" : language;
        for (GraphArc arc : getAllArcs()) updateDescription((LabelledGraphArc)arc, (Relationship)arc.getUserObject());
    }
    public IRI getLabelAnnotation() { return labelAnnotation; }
    public String getLabelLanguage() { return labelLanguage; }
    private String label(Relationship relationship) {
        if (labelAnnotation != null) {
            List<OWLLiteral> values = new ArrayList<>();
            for (Relationship.Support support : relationship.getSupport()) for (OWLAnnotation annotation : support.axiom.getAnnotations()) {
                if (annotation.getProperty().getIRI().equals(labelAnnotation) && annotation.getValue() instanceof OWLLiteral)
                    values.add((OWLLiteral)annotation.getValue());
            }
            values.sort(Comparator.comparingInt((OWLLiteral value) -> value.getLang().equalsIgnoreCase(labelLanguage) ? 0 : value.hasLang() ? 2 : 1)
                    .thenComparing(OWLLiteral::getLiteral).thenComparing(OWLLiteral::getLang));
            if (!values.isEmpty()) return values.get(0).getLiteral();
        }
        return relationship.label(this::render);
    }
    public String describe(Relationship relationship) {
        StringBuilder text = new StringBuilder(relationship.label(this::render));
        text.append(relationship.kind == Relationship.Kind.DOMAIN_RANGE ? "\nDomain/range constraint summary" : "\nAsserted axiom projection");
        if (relationship.property != null) text.append("\nProperty: ").append(relationship.property.getNamedProperty().getIRI());
        for (Relationship.Support support : relationship.getSupport()) {
            text.append("\n\nOntology: ").append(support.ontology.getOntologyID());
            text.append("\n").append(manager.getRendering(support.axiom));
            for (OWLAnnotation annotation : support.axiom.getAnnotations()) text.append("\n").append(annotation);
        }
        return text.toString();
    }
    private void updateDescription(LabelledGraphArc arc, Relationship relationship) {
        arc.setLabelText(label(relationship));
        arc.setTooltip(describe(relationship));
    }
    protected GraphNode addNode(OWLEntity entity) {
        GraphNode existing = getNode(entity);
        if (existing != null) return existing;
        GraphNode node = addNode(entity, manager.getRendering(entity), icons.getIcon(entity), entity.isOWLClass() ? "class" : "individual");
        node.setFixedLocation(pinned.contains(entity));
        return node;
    }
    private GraphArc addRelationship(Relationship relationship) {
        GraphArc found = getArc(relationship);
        if (found != null) return found;
        GraphNode source = addNode(relationship.source), target = addNode(relationship.target);
        LabelledGraphArc arc = new LabelledGraphArc(relationship, source, target, null,
                relationship.type(this::render), () -> labelMode);
        // Every stored edge uses the same source-to-target direction as its phrase.
        arc.setInverted(false);
        updateDescription(arc, relationship);
        addArcInternal(arc);
        arrangeArcs(source, target);
        return arc;
    }
    public void show(OWLEntity entity, FilterManager filters) {
        filterManager = filters;
        addNode(entity);
        for (Relationship relationship : getRelationshipIndex().incident(entity)) {
            if (allowed(relationship) && getNode(relationship.source) != null && getNode(relationship.target) != null) addRelationship(relationship);
        }
        recalculateArcStyles();
    }
    private boolean allowed(Relationship relationship) {
        Object type = relationship.type(this::render);
        return (filterManager == null || filterManager.isArcTypeVisible(type))
                && (restrictToArcType == null || restrictToArcType.equals(type));
    }
    public void showNeighborhood(OWLEntity entity, boolean replace) { showNeighborhood(Collections.singleton(entity), replace); }
    public void showNeighborhood(Collection<? extends OWLEntity> roots, boolean replace) {
        Set<Relationship> candidates = new LinkedHashSet<>();
        for (OWLEntity entity : roots) for (Relationship relationship : getRelationshipIndex().incident(entity)) if (allowed(relationship)) candidates.add(relationship);
        Set<Relationship> retained = new HashSet<>();
        Set<OWLEntity> retainedNodes = new HashSet<>(roots);
        omitted = 0;
        int added = 0;
        for (Relationship relationship : candidates) {
            if (getArc(relationship) == null && added >= expansionLimit) { omitted++; continue; }
            if (getArc(relationship) == null) added++;
            retained.add(relationship); retainedNodes.add(relationship.source); retainedNodes.add(relationship.target);
            addRelationship(relationship);
        }
        for (OWLEntity entity : roots) addNode(entity);
        if (replace) {
            for (GraphArc arc : new ArrayList<>(getAllArcs())) if (!retained.contains(arc.getUserObject())) removeArc(arc.getUserObject());
            for (GraphNode node : new ArrayList<>(getAllNodes())) if (!retainedNodes.contains(node.getUserObject()) && !pinned.contains(node.getUserObject())) removeNode(node.getUserObject());
        }
        recalculateArcStyles();
    }
    public Collection<? extends OWLEntity> search(String pattern, FilterManager filters) {
        filterManager = filters; restrictToArcType = null;
        Set<OWLEntity> matches = new LinkedHashSet<>();
        matches.addAll(manager.getOWLEntityFinder().getMatchingOWLClasses(pattern, true, Pattern.CASE_INSENSITIVE));
        matches.addAll(manager.getOWLEntityFinder().getMatchingOWLIndividuals(pattern, true, Pattern.CASE_INSENSITIVE));
        showNeighborhood(matches, true);
        return matches;
    }
    public void refreshVisible() { invalidateRelationships(); refreshFromIndex(); }
    public void refreshFromIndex() {
        List<OWLEntity> visible = new ArrayList<>();
        for (GraphNode node : new ArrayList<>(getAllNodes())) {
            OWLEntity entity = (OWLEntity)node.getUserObject();
            boolean builtin = entity.isOWLClass() && (entity.asOWLClass().isOWLThing() || entity.asOWLClass().isOWLNothing());
            if (builtin || getRelationshipIndex().entities().contains(entity)) {
                visible.add(entity); ((DefaultGraphNode)node).setText(manager.getRendering(entity));
            } else { removeNode(entity); pinned.remove(entity); }
        }
        for (GraphArc arc : new ArrayList<>(getAllArcs())) removeArc(arc.getUserObject());
        for (OWLEntity entity : visible) show(entity, filterManager);
    }
    @Override public void clear() { super.clear(); pinned.clear(); omitted = 0; }
    @Override public Collection<Object> getNodeTypes() { return Arrays.<Object>asList("class", "individual"); }
    @Override public Collection<Object> getArcTypes() {
        if (arcTypes == null) {
            Set<Object> types = new LinkedHashSet<>();
            types.add(DIRECT_SUBCLASS_SLOT_TYPE); types.add(DIRECT_INDIVIDUAL_SLOT_TYPE);
            if (index != null) for (Relationship relationship : index.all()) types.add(relationship.type(this::render));
            arcTypes = Collections.unmodifiableCollection(types);
        }
        return arcTypes;
    }
    public Set<Object> getRelationshipTypes(OWLEntity entity) {
        Set<Object> types = new LinkedHashSet<>();
        for (Relationship relationship : getRelationshipIndex().incident(entity)) types.add(relationship.type(this::render));
        return types;
    }
    public void resetNodeToArcCount() { /* Counts use the immutable index and current filters. */ }
    public Set<GraphArc> getCachedArcsForEntity(OWLEntity entity) {
        Set<GraphArc> arcs = new LinkedHashSet<>();
        for (Relationship relationship : getRelationshipIndex().incident(entity)) if (allowed(relationship))
            arcs.add(new DefaultGraphArc(relationship, new DefaultGraphNode(relationship.source), new DefaultGraphNode(relationship.target), relationship.type(this::render)));
        return arcs;
    }
    public boolean isExpandable(GraphNode node) {
        if (index == null) return false;
        for (Relationship relationship : index.incident((OWLEntity)node.getUserObject())) if (allowed(relationship)) return true;
        return false;
    }
    public boolean isExpanded(GraphNode node) {
        if (index == null) return false;
        for (Relationship relationship : index.incident((OWLEntity)node.getUserObject())) if (allowed(relationship) && getArc(relationship) == null) return false;
        return true;
    }
    public void expandNode(GraphNode node) { showNeighborhood((OWLEntity)node.getUserObject(), false); }
    public void expandNode(GraphNode node, Object type) {
        restrictToArcType = type;
        try { expandNode(node); } finally { restrictToArcType = null; }
    }
    public void collapseNode(GraphNode root) { collapse(root, new HashSet<OWLEntity>()); }
    private void collapse(GraphNode root, Set<OWLEntity> seen) {
        if (!seen.add((OWLEntity)root.getUserObject())) return;
        for (GraphArc arc : new ArrayList<>(root.getArcs())) {
            if (arc.getSource() != root || pinned.contains(arc.getDestination().getUserObject())) continue;
            GraphNode target = arc.getDestination();
            removeArc(arc.getUserObject());
            if (target != root && ((Relationship)arc.getUserObject()).isHierarchy()) collapse(target, seen);
            if (target != root && target.getArcs().isEmpty()) removeNode(target.getUserObject());
        }
    }
    public void hideAscendants(GraphNode root) {
        hideAscendants(root, new HashSet<OWLEntity>());
    }
    private void hideAscendants(GraphNode root, Set<OWLEntity> seen) {
        if (!seen.add((OWLEntity)root.getUserObject())) return;
        for (GraphArc arc : new ArrayList<>(root.getArcs())) {
            if (arc.getDestination() != root || arc.getSource() == root || pinned.contains(arc.getSource().getUserObject())) continue;
            GraphNode source = arc.getSource();
            removeArc(arc.getUserObject());
            if (((Relationship)arc.getUserObject()).isHierarchy()) hideAscendants(source, seen);
            if (source.getArcs().isEmpty()) removeNode(source.getUserObject());
        }
    }
}
