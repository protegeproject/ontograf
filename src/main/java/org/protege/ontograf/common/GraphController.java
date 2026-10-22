/**
 * Copyright 1998-2007, CHISEL Group, University of Victoria, Victoria, BC, Canada.
 * All rights reserved.
 */
package org.protege.ontograf.common;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.eclipse.zest.layouts.algorithms.DirectedGraphLayoutAlgorithm;
import org.eclipse.zest.layouts.algorithms.HorizontalDirectedGraphLayoutAlgorithm;
import org.eclipse.zest.layouts.progress.ProgressEvent;
import org.eclipse.zest.layouts.progress.ProgressListener;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.ontograf.common.util.IconConstants;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;

import ca.uvic.cs.chisel.cajun.actions.CajunAction;
import ca.uvic.cs.chisel.cajun.actions.LayoutAction;
import ca.uvic.cs.chisel.cajun.constants.LayoutConstants;
import ca.uvic.cs.chisel.cajun.filter.FilterChangedEvent;
import ca.uvic.cs.chisel.cajun.filter.FilterChangedListener;
import ca.uvic.cs.chisel.cajun.graph.FlatGraph;
import ca.uvic.cs.chisel.cajun.graph.Graph;
import ca.uvic.cs.chisel.cajun.graph.arc.DefaultGraphArcStyle;
import ca.uvic.cs.chisel.cajun.graph.arc.GraphArc;
import ca.uvic.cs.chisel.cajun.graph.node.DefaultGraphNode;
import ca.uvic.cs.chisel.cajun.graph.node.DefaultGraphNodeStyle;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import ca.uvic.cs.chisel.cajun.graph.ui.DefaultFlatGraphView;
import edu.umd.cs.piccolo.util.PBounds;

/**
 * Controller for the graph and model elements. This controller ties the appropriate graph model
 * representation (Protege OWL) to the graph.
 *
 * @author seanf
 */
public class GraphController {
	/** duration to animate the zoom in and zoom out */
	private static final int ANIMATION_DURATION = 700;

	/** the graph object, performs layouts and renders the model */
	private FlatGraph graph;

	/** the model representation of the graph, nodes and edges */
	private ProtegeGraphModel model;

	/** the panel that renders the graph view */
	private DefaultFlatGraphView view;

	private Action expandAction;
	private Action collapseAction;
	private Set<Action> expandBasedOnActions;
	public static final String REGEXP = "regexp";
	public static final String EXACT_MATCH = "exact match";
	public static final String ENDS_WITH = "ends with";
	public static final String STARTS_WITH = "starts with";
	public static final String CONTAINS = "contains";

	private OWLEntity owlClass;
    private final org.protege.editor.owl.model.OWLModelManager modelManager;
    private final javax.swing.Timer refreshTimer;
    private final org.semanticweb.owlapi.model.OWLOntologyChangeListener ontologyListener;
    private final org.protege.editor.owl.model.event.OWLModelManagerListener managerListener;
    private volatile boolean disposed;
    private boolean ontologySwitched;
    private long refreshGeneration;
    private javax.swing.SwingWorker<org.protege.ontograf.model.RelationshipIndex, Void> indexWorker;
    private final javax.swing.JLabel status = new javax.swing.JLabel();
    private void rebuildIndex() {
        if (indexWorker != null) indexWorker.cancel(true);
        final long generation = ++refreshGeneration;
        final java.util.Map<org.semanticweb.owlapi.model.OWLOntology, java.util.Set<org.semanticweb.owlapi.model.OWLAxiom>> snapshot =
                org.protege.ontograf.model.RelationshipIndex.snapshot(modelManager.getActiveOntologies());
        final org.semanticweb.owlapi.model.OWLDataFactory factory = modelManager.getOWLDataFactory();
        status.setText("Indexing ontology…");
        indexWorker = new javax.swing.SwingWorker<org.protege.ontograf.model.RelationshipIndex, Void>() {
            protected org.protege.ontograf.model.RelationshipIndex doInBackground() {
                return new org.protege.ontograf.model.RelationshipIndex(snapshot, factory);
            }
            protected void done() {
                if (disposed || isCancelled() || generation != refreshGeneration) return;
                try {
                    model.installIndex(get()); model.refreshFromIndex();
                    updateStatus(); graph.repaint();
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                catch (java.util.concurrent.ExecutionException ex) {
                    status.setText("Ontology index failed; retry with Refresh");
                    org.slf4j.LoggerFactory.getLogger(GraphController.class).error("Cannot refresh OntoGraf", ex.getCause());
                }
            }
        };
        indexWorker.execute();
    }
    public void updateStatus() {
        status.setText(model.getAllNodes().size() + " nodes, " + model.getAllArcs().size() + " edges"
                + (model.getOmittedCount() == 0 ? "" : "; " + model.getOmittedCount() + " more edges — expand again"));
    }
    public void refreshOntology() { scheduleRefresh(false); }
    public void cancelRefresh() {
        refreshGeneration++; refreshTimer.stop();
        if (indexWorker != null) indexWorker.cancel(true);
        status.setText("Refresh cancelled");
    }
    private final org.protege.editor.core.prefs.Preferences preferences;
    private final java.beans.PropertyChangeSupport properties = new java.beans.PropertyChangeSupport(this);
    public void addLabelModeListener(java.beans.PropertyChangeListener listener) { properties.addPropertyChangeListener("labelMode", listener); }

    public void setLabelMode(org.protege.ontograf.ui.EdgeLabelMode mode) {
        org.protege.ontograf.ui.EdgeLabelMode previous = model.getLabelMode();
        model.setLabelMode(mode);
        properties.firePropertyChange("labelMode", previous, mode);
        preferences.putString("edgeLabelMode", mode.name());
        graph.repaint();
    }

    private void scheduleRefresh(boolean switched) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            ontologySwitched |= switched;
            refreshGeneration++;
            if (indexWorker != null) indexWorker.cancel(true);
            refreshTimer.restart();
        });
    }

    public void dispose() {
        disposed = true;
        cancelRefresh();
        modelManager.removeOntologyChangeListener(ontologyListener);
        modelManager.removeListener(managerListener);
        graph.getRoot().getActivityScheduler().removeAllActivities();
        graph.clear();
        model.clear();
        model.invalidateRelationships();
    }

	public GraphController(Container parentContainer, OWLEditorKit owlEditorKit) {
		model = new ProtegeGraphModel(owlEditorKit);
        modelManager = owlEditorKit.getOWLModelManager();
        preferences = org.protege.editor.core.prefs.PreferencesManager.getInstance()
                .getApplicationPreferences(GraphController.class);
        model.setLabelMode(org.protege.ontograf.ui.EdgeLabelMode.fromPreference(
                preferences.getString("edgeLabelMode", "SELECTED")));
        refreshTimer = new javax.swing.Timer(150, event -> {
            if (disposed) return;
            if (ontologySwitched) { clear(); back.clear(); forward.clear(); ontologySwitched = false; }
            rebuildIndex();
        });
        refreshTimer.setRepeats(false);
        ontologyListener = changes -> scheduleRefresh(false);
        managerListener = event -> scheduleRefresh(event.isType(
                org.protege.editor.owl.model.event.EventType.ACTIVE_ONTOLOGY_CHANGED));
        modelManager.addOntologyChangeListener(ontologyListener);
        modelManager.addListener(managerListener);

		this.graph = new FlatGraph(model);
		this.graph.setShowNodeTooltips(false);

		// show the plus icon for expandable nodes
		DefaultGraphNodeStyle nodeStyle = new DefaultGraphNodeStyle() {
            public Collection<Icon> getOverlayIcons(GraphNode graphNode) {
                Collection<Icon> overlays = new ArrayList<>();
                if (model.getPinnedEntities().contains(graphNode.getUserObject())) overlays.add(IconConstants.ICON_PIN_TOOLTIPS);
                if (model.isExpandable(graphNode) && !model.isExpanded(graphNode)) overlays.add(IconConstants.ICON_PLUS);
                return overlays;
            }
		};
		nodeStyle.setNodeTypes(model.getNodeTypes());
		this.graph.setGraphNodeStyle(nodeStyle);

		// color the arcs based on arc type
		DefaultGraphArcStyle arcStyle = new DefaultGraphArcStyle() {
			public Stroke getStroke(GraphArc arc) {
				if (ProtegeGraphModel.DIRECT_SUBCLASS_SLOT_TYPE.equals(arc.getType()) || ProtegeGraphModel.DIRECT_INDIVIDUAL_SLOT_TYPE.equals(arc.getType())) {
					setDashed(false);
				} else {
					setDashed(true);
					if (arc.getType() instanceof org.protege.ontograf.model.Relationship.Type && ((org.protege.ontograf.model.Relationship.Type)arc.getType()).isEquivalent()) {
						setDashedCapSquare(BasicStroke.CAP_ROUND);
						setDashWidth(2f);
					}
					else {
						setDashedCapSquare(BasicStroke.CAP_SQUARE);
						setDashWidth(10f);
					}
				}

				return super.getStroke(arc);
			}
		};
		arcStyle.setArcTypes(model.getArcTypes());
		this.graph.setGraphArcStyle(arcStyle);

		initialize(parentContainer);
        scheduleRefresh(false);

		this.graph.addLayoutListener(new ProgressListener() {
			public void progressEnded(ProgressEvent arg0) {
				// make sure the node is visible
				DefaultGraphNode node = (DefaultGraphNode)model.getNode(owlClass);
				panTo(node);
			}

			public void progressStarted(ProgressEvent arg0) {}
			public void progressUpdated(ProgressEvent arg0) {}
		});

		this.graph.getFilterManager().addFilterChangedListener(new FilterChangedListener() {
			public void filtersChanged(FilterChangedEvent fce) {
				// have to update the relationship counts when a filter is applied
				model.resetNodeToArcCount();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (!disposed && !restoring) { model.refreshFromIndex(); graph.getFilterManager().applyFilters(model); updateStatus(); graph.repaint(); }
                });
			}
		});
	}

    private final java.util.Deque<org.protege.ontograf.io.GraphState> back = new java.util.ArrayDeque<>();
    private final java.util.Deque<org.protege.ontograf.io.GraphState> forward = new java.util.ArrayDeque<>();
    private boolean restoring;
    public static String filterKey(Object type) {
        return type instanceof org.protege.ontograf.model.Relationship.Type
                ? ((org.protege.ontograf.model.Relationship.Type)type).storageKey() : type.toString();
    }
    public String ontologyKey() {
        org.semanticweb.owlapi.model.OWLOntology ontology = modelManager.getActiveOntology();
        if (!ontology.isAnonymous()) return ontology.getOntologyID().toString();
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            java.util.List<String> signature = new java.util.ArrayList<>();
            for (OWLEntity entity : ontology.getSignature()) signature.add(entity.getEntityType().getName() + ":" + entity.getIRI());
            java.util.Collections.sort(signature);
            for (String entry : signature) digest.update((entry + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "anonymous-signature:" + java.util.Base64.getEncoder().encodeToString(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public org.protege.ontograf.io.GraphState captureState() {
        org.protege.ontograf.io.GraphState state = new org.protege.ontograf.io.GraphState();
        state.ontology = ontologyKey(); state.labelMode = model.getLabelMode().name();
        state.annotation = model.getLabelAnnotation() == null ? "" : model.getLabelAnnotation().toString();
        state.language = model.getLabelLanguage();
        if (graph.getLastLayout() != null) state.layout = graph.getLastLayout().getName();
        graph.getCamera().getViewTransformReference().getMatrix(state.camera);
        for (GraphNode node : model.getAllNodes()) {
            OWLEntity entity = (OWLEntity)node.getUserObject();
            state.nodes.add(new org.protege.ontograf.io.GraphState.Node(entity.getEntityType().getName(), entity.getIRI().toString(),
                    node.getBounds().getX(), node.getBounds().getY(), model.getPinnedEntities().contains(entity)));
        }
        graph.getFilterManager().getArcTypesMap().forEach((key, value) -> state.filters.put(filterKey(key), value));
        graph.getFilterManager().getNodeTypesMap().forEach((key, value) -> state.nodeFilters.put(key.toString(), value));
        return state;
    }
    public int restoreState(org.protege.ontograf.io.GraphState state) throws java.io.IOException {
        String legacyKey = modelManager.getActiveOntology().getOntologyID().getOntologyIRI().toString();
        if (!state.ontology.equals(ontologyKey()) && !(state.legacy && state.ontology.equals(legacyKey)))
            throw new java.io.IOException("The saved graph belongs to another ontology. The current view has been preserved.");
        model.getRelationshipIndex();
        java.util.Map<OWLEntity, org.protege.ontograf.io.GraphState.Node> resolved = new java.util.LinkedHashMap<>();
        int missing = 0;
        for (org.protege.ontograf.io.GraphState.Node saved : state.nodes) {
            boolean found = false;
            for (OWLEntity entity : modelManager.getOWLEntityFinder().getEntities(org.semanticweb.owlapi.model.IRI.create(saved.iri))) {
                if (saved.type.isEmpty() || entity.getEntityType().getName().equals(saved.type)) { resolved.put(entity, saved); found = true; }
            }
            if (!found) missing++;
        }
        if (!state.nodes.isEmpty() && resolved.isEmpty()) throw new java.io.IOException("None of the saved entities exist in the active ontology. The current view has been preserved.");
        remember();
        restoring = true;
        try {
            graph.getRoot().getActivityScheduler().removeAllActivities();
            graph.clear(); model.clear();
            org.protege.ontograf.ui.EdgeLabelMode previousMode = model.getLabelMode();
            model.setLabelMode(org.protege.ontograf.ui.EdgeLabelMode.valueOf(state.labelMode));
            properties.firePropertyChange("labelMode", previousMode, model.getLabelMode());
            model.setLabelAnnotation(state.annotation.isEmpty() ? null : org.semanticweb.owlapi.model.IRI.create(state.annotation), state.language);
            for (Object type : model.getArcTypes()) graph.getFilterManager().setArcTypeVisible(type, state.filters.getOrDefault(filterKey(type), true));
            for (Object type : model.getNodeTypes()) graph.getFilterManager().setNodeTypeVisible(type, state.nodeFilters.getOrDefault(type.toString(), true));
            for (OWLEntity entity : resolved.keySet()) model.show(entity, graph.getFilterManager());
            for (java.util.Map.Entry<OWLEntity, org.protege.ontograf.io.GraphState.Node> entry : resolved.entrySet()) {
                model.getNode(entry.getKey()).setLocation(entry.getValue().x, entry.getValue().y);
                model.setPinned(entry.getKey(), entry.getValue().pinned);
            }
            for (LayoutAction action : graph.getLayouts()) if (action.getName().equals(state.layout)) graph.setLastLayout(action);
            graph.getCamera().setViewTransform(new java.awt.geom.AffineTransform(state.camera));
            graph.getFilterManager().applyFilters(model);
            graph.repaint();
        } finally { restoring = false; }
        return missing;
    }
    private void remember() {
        if (restoring) return;
        back.push(captureState()); while (back.size() > 50) back.removeLast(); forward.clear();
    }
    public void navigate(boolean previous) {
        java.util.Deque<org.protege.ontograf.io.GraphState> from = previous ? back : forward;
        java.util.Deque<org.protege.ontograf.io.GraphState> to = previous ? forward : back;
        if (from.isEmpty()) return;
        org.protege.ontograf.io.GraphState state = from.pop();
        to.push(captureState()); restoring = true;
        try { restoreState(state); }
        catch (java.io.IOException ex) { javax.swing.JOptionPane.showMessageDialog(view, ex.getMessage(), "Cannot restore view", javax.swing.JOptionPane.WARNING_MESSAGE); }
        finally { restoring = false; }
    }

	public ProtegeGraphModel getModel() {
		return model;
	}

	public Graph getGraph() {
		return graph;
	}

	public void refresh() {
		graph.repaint();
	}

	public void clear() {
        cancelRefresh();
		graph.clear();
		model.clear();
		model.invalidateRelationships();
        updateStatus();
	}

	public void display(OWLClass cls) {
		model.showNeighborhood(cls, true);
		graph.performLayout();
        updateStatus();
	}

	public void displayAsSingleNode(OWLEntity entity) {
		model.show(entity, graph.getFilterManager());
		graph.performLayout();
        updateStatus();
	}

	public JToolBar getToolBar() {
		return view.getToolBar();
	}

	/**
	 * Performs a search on the ontology with the given string.
	 *
	 * @param searchString The entered string to search on
	 * @param searchMode The search type mode that determines how to match the searchString
	 */
	public int search(String searchString, String searchMode) {
		searchString = prepareSearchString(searchString, searchMode);
        remember();

		for(GraphNode node : model.getAllNodes()) node.setSelected(false);

		Collection<GraphNode> matchingNodes = new ArrayList<GraphNode>();
		Collection<? extends OWLEntity> searchResults = model.search(searchString, graph.getFilterManager());
		for (OWLEntity owlEntity : searchResults) {
			GraphNode node = model.getNode(owlEntity);
			if(node != null) {
				matchingNodes.add(model.getNode(owlEntity));
			}
		}

		graph.setSelectedNodes(matchingNodes);
		graph.setMatchingNodes(matchingNodes);
		graph.performLayout();
        updateStatus();

		//model.recalculateArcTypes();

		return searchResults.size();
	}

	/**
	 * Shows the class as a node and any arcs that exist between this new node and the
	 * existing nodes on the canvas.
	 *
	 * @param owlClass
	 */
	public void showOWLClass(OWLClass owlClass) {
        remember();
		// set all the current nodes to a fixed location
		for(GraphNode node : model.getAllNodes()) {
			node.setFixedLocation(true);
		}

		boolean panToNode = true;
		if(model.getNode(owlClass) == null) {
			panToNode = false;
		}

		model.restrictToArcType = null;
		model.show(owlClass, graph.getFilterManager());

		Collection<GraphNode> matchingNodes = new ArrayList<GraphNode>();
		matchingNodes.add(model.getNode(owlClass));

		graph.setMatchingNodes(matchingNodes);
		graph.performLayout();
        updateStatus();

		// unset the fixed location on all nodes
		for(GraphNode node : model.getAllNodes()) {
			node.setFixedLocation(model.getPinnedEntities().contains(node.getUserObject()));
		}

		this.owlClass = owlClass;

		if(panToNode) {
			// make sure the node is visible
			DefaultGraphNode node = (DefaultGraphNode)model.getNode(owlClass);
			panTo(node);
		}
	}

	private void panTo(DefaultGraphNode node) {
		if(node != null) {
			double x = node.getFullBoundsReference().getX();
			double y = node.getFullBoundsReference().getY();
			double w = node.getFullBoundsReference().getWidth();
			double h = node.getFullBoundsReference().getHeight();
			PBounds bounds = new PBounds(x - w * .01, y - h * .02, w + w * .02, h + h * .04);
			// only pan to the bounds if the node is not already visible
			if(!graph.getCamera().getViewBounds().contains(bounds.getBounds2D())) {
				graph.getRoot().getActivityScheduler().addActivity(
						graph.getCamera().animateViewToCenterBounds(bounds.getBounds2D(), false, ANIMATION_DURATION)
				);
			}
		}
	}

	/**
	 * Adds appropriate query parameters to the searchString based on the selected searchMode.
	 */
	static String prepareSearchString(String searchString, String searchMode) {
        if (!REGEXP.equals(searchMode)) searchString = java.util.regex.Pattern.quote(searchString);
        java.util.regex.Pattern.compile(searchString);
		if (searchMode.equals(CONTAINS)) {
			return searchString;
		} else if (searchMode.equals(STARTS_WITH)) {
			return "^" + searchString;
		} else if (searchMode.equals(ENDS_WITH)) {
			return searchString + "$";
		} else if(searchMode.equals(EXACT_MATCH)) {
			return "^" + searchString + "$";
		}

		return searchString;
	}

	private void initialize(Container parentContainer) {
		// setup the layouts
		List<Object> layoutRelTypes = new ArrayList<Object>();
		layoutRelTypes.add(ProtegeGraphModel.DIRECT_SUBCLASS_SLOT_TYPE);
		layoutRelTypes.add(ProtegeGraphModel.DIRECT_INDIVIDUAL_SLOT_TYPE);
		for (LayoutAction layoutAction : graph.getLayouts()) {
			if (layoutAction.getName().equals(LayoutConstants.LAYOUT_TREE_HORIZONTAL)) {
				layoutAction.setLayout(new HorizontalDirectedGraphLayoutAlgorithm());
				this.graph.setLastLayout(layoutAction);
				layoutAction.setLayoutRelTypes(layoutRelTypes);
			} else if (layoutAction.getName().equals(LayoutConstants.LAYOUT_TREE_VERTICAL)) {
				layoutAction.setLayout(new DirectedGraphLayoutAlgorithm());
				layoutAction.setLayoutRelTypes(layoutRelTypes);
			}
		}

		view = new DefaultFlatGraphView(graph);

		parentContainer.add(view, BorderLayout.CENTER);
        parentContainer.add(status, BorderLayout.SOUTH);
        model.addGraphModelListener(new ca.uvic.cs.chisel.cajun.graph.GraphModelAdapter() {
            @Override public void graphArcAdded(GraphArc arc) { updateStatus(); }
            @Override public void graphArcRemoved(GraphArc arc) { updateStatus(); }
            @Override public void graphNodeAdded(GraphNode node) { updateStatus(); }
            @Override public void graphNodeRemoved(GraphNode node) { updateStatus(); }
        });

		initNodeMenu(graph.getNodeContextMenu());
		graph.addInputEventListener(new ProtegeInputEventHandler(model, graph));
	}

	/**
	 * Initializes the menu for the right-click operation on a graph node.
	 *
	 * @param graphMenu
	 */
	private void initNodeMenu(JPopupMenu graphMenu) {
		graphMenu.add(new CajunAction("Show neighborhood", "Show neighborhood") {
			private static final long serialVersionUID = 4234994986788687910L;

			public void actionPerformed(ActionEvent e) {
				owlClass = (OWLEntity) graph.getFirstSelectedNode().getUserObject();
				remember();
            model.showNeighborhood(owlClass, true);
				graph.performLayout();
        updateStatus();
			}
		});

		graphMenu.add(new CajunAction("Set as focus", "Set as focus") {
			private static final long serialVersionUID = -68720636770886830L;

			public void actionPerformed(ActionEvent e) {
				owlClass = (OWLEntity)graph.getFirstSelectedNode().getUserObject();
				model.hideAscendants(graph.getFirstSelectedNode());
				graph.performLayout();
        updateStatus();
			}
		});

		graphMenu.addSeparator();

		expandAction = new CajunAction("Expand", "Expand") {
			private static final long serialVersionUID = -8044342001264176639L;

			public void actionPerformed(ActionEvent e) {
				owlClass = (OWLEntity)graph.getFirstSelectedNode().getUserObject();
				remember();
                model.expandNode(graph.getFirstSelectedNode());
				graph.performLayout();
        updateStatus();
			}
		};

		collapseAction = new CajunAction("Collapse", "Collapse") {
			private static final long serialVersionUID = 815274665395852446L;

			public void actionPerformed(ActionEvent e) {
				owlClass = (OWLEntity)graph.getFirstSelectedNode().getUserObject();
				remember();
                model.collapseNode(graph.getFirstSelectedNode());
				graph.performLayout();
        updateStatus();
			}
		};

		graphMenu.add(expandAction);
		graphMenu.add(collapseAction);
		graphMenu.addSeparator();

		final JMenu expandBasedOnMenu = new JMenu("Expand on");
		graphMenu.add(expandBasedOnMenu);

		expandBasedOnActions = new HashSet<Action>();
		// initialize the actions with the defaults
//		for(Object o : model.getArcTypes()) {
//			Action action = getNodeExpansionAction(o.toString());
//			expandBasedOnActions.add(action);
//			expandBasedOnMenu.add(action);
//		}

		// add any new arc types
//		model.addGraphModelListener(new GraphModelAdapter() {
//			public void graphArcTypeAdded(Object arcType) {
//				Action action = getNodeExpansionAction(arcType.toString());
//				if(!expandBasedOnActions.contains(action)) {
//					expandBasedOnActions.add(action);
//					expandBasedOnMenu.add(action);
//				}
//			}
//		});

		graphMenu.add(expandBasedOnMenu);

		graphMenu.addPopupMenuListener(new PopupMenuListener() {
			public void popupMenuCanceled(PopupMenuEvent e) {}
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				GraphNode node = graph.getFirstSelectedNode();
				if (node != null) {
					prepareNodeSpecificActions(node, expandBasedOnMenu);
					initNodeActionConditions(node);
				}
			}

		});
	}

	/**
	 * Sets the expansion menu options enabled/disabled flag based on the current state of the
	 * node. If the node has already been expanded, everything is disabled. Otherwise, all the
	 * non-filtered arc types are enabled.
	 * @param node
	 */
	private void initNodeActionConditions(GraphNode node) {
		if (model.isExpanded(node)) {
			expandAction.setEnabled(false);
			for (Action action : expandBasedOnActions) {
				action.setEnabled(false);
			}
		} else {
			expandAction.setEnabled(true);
			for (Action action : expandBasedOnActions) {
				if(graph.getFilterManager().isArcTypeVisible(action.getValue("relationshipType"))) {
					action.setEnabled(true);
				}
				else {
					action.setEnabled(false);
				}
			}
		}
	}

	/**
	 * Uses the arc cache to get all the unique arc types for the given node.
	 */
	private void prepareNodeSpecificActions(GraphNode node, JMenu expandBasedOnMenu) {
		expandBasedOnActions.clear();
		expandBasedOnMenu.removeAll();
		for (Object type : model.getRelationshipTypes((OWLEntity)node.getUserObject())) {
            Action action = getNodeExpansionAction(type);
			if(!expandBasedOnActions.contains(action)) {
				expandBasedOnActions.add(action);
				expandBasedOnMenu.add(action);
			}
		}
	}

	private CajunAction getNodeExpansionAction(Object arcType) {
		CajunAction action = new CajunAction(arcType.toString(), arcType.toString()) {
			private static final long serialVersionUID = -4658385618425759291L;

			public void actionPerformed(ActionEvent e) {
				model.expandNode(graph.getFirstSelectedNode(), arcType);
				graph.performLayout();
        updateStatus();
			}
		};
        action.putValue("relationshipType", arcType);
        return action;
	}
}
