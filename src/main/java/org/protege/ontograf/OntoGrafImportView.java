package org.protege.ontograf;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.Icon;

import org.eclipse.zest.layouts.algorithms.HorizontalDirectedGraphLayoutAlgorithm;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.ui.view.cls.AbstractOWLClassViewComponent;
import org.protege.ontograf.common.util.OWLIconProviderImpl;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;

import ca.uvic.cs.chisel.cajun.actions.LayoutAction;
import ca.uvic.cs.chisel.cajun.constants.LayoutConstants;
import ca.uvic.cs.chisel.cajun.graph.DefaultGraphModel;
import ca.uvic.cs.chisel.cajun.graph.FlatGraph;
import ca.uvic.cs.chisel.cajun.graph.arc.DefaultGraphArc;
import ca.uvic.cs.chisel.cajun.graph.arc.GraphArc;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import ca.uvic.cs.chisel.cajun.graph.ui.DefaultFlatGraphView;

/**
 * Plugin extension point for the OntoGraf imports view.
 *
 * @author seanf
 */
public class OntoGrafImportView extends AbstractOWLClassViewComponent  {
	private static final long serialVersionUID = -6969495880634875570L;

	/** the graph object, performs layouts and renders the model */
	private FlatGraph graph;

	private ImportsGraphModel model;

	/** the panel that renders the graph view */
	private DefaultFlatGraphView view;
    private org.protege.editor.owl.model.event.OWLModelManagerListener managerListener;
    private org.semanticweb.owlapi.model.OWLOntologyChangeListener changeListener;
    private javax.swing.Timer refreshTimer;
    private volatile boolean disposed;

	@Override
	public void initialiseClassView() throws Exception {
		setLayout(new BorderLayout());

		this.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				// TODO Auto-generated method stub
				super.componentResized(e);

			}
		});

		this.model = new ImportsGraphModel(this.getOWLEditorKit());
		this.graph = new FlatGraph(model);
		this.view = new DefaultFlatGraphView(graph);
        refreshTimer = new javax.swing.Timer(150, event -> {
            if (!disposed) { model.reload(); graph.performLayout(); }
        });
        refreshTimer.setRepeats(false);
        Runnable refresh = () -> javax.swing.SwingUtilities.invokeLater(() -> { if (!disposed) refreshTimer.restart(); });
        managerListener = event -> refresh.run();
        changeListener = changes -> { if (changes.stream().anyMatch(change -> change.isImportChange())) refresh.run(); };
        getOWLModelManager().addListener(managerListener);
        getOWLModelManager().addOntologyChangeListener(changeListener);

		this.add(this.view, BorderLayout.CENTER);

		Dimension d = new Dimension(800, 600);
		setPreferredSize(d);
		setSize(d);
		setLocation(100, 50);

		setVisible(true);

		for (LayoutAction layoutAction : graph.getLayouts()) {
			if (layoutAction.getName().equals(LayoutConstants.LAYOUT_SPRING)) {
				layoutAction.setLayout(new HorizontalDirectedGraphLayoutAlgorithm());
				this.graph.setLastLayout(layoutAction);
			}
		}

		this.graph.performLayout();
	}

	@Override
	protected OWLClass updateView(OWLClass owlClass) {
		return null;
	}

	@Override
	public void disposeView() {
        disposed = true;
        if (refreshTimer != null) refreshTimer.stop();
        if (managerListener != null) getOWLModelManager().removeListener(managerListener);
        if (changeListener != null) getOWLModelManager().removeOntologyChangeListener(changeListener);
        if (graph != null) {
            graph.getRoot().getActivityScheduler().removeAllActivities();
            graph.clear();
            model.clear();
        }

	}
}

class ImportsGraphModel extends DefaultGraphModel {
	private static final String IMPORTS = "imports";
	private static final String ONTOLOGY = "ontology";

	private OWLEditorKit owlEditorKit;

	public ImportsGraphModel(OWLEditorKit owlEditorKit) {
		super();

		this.owlEditorKit = owlEditorKit;

		loadData(owlEditorKit.getOWLWorkspace().getOWLModelManager().getActiveOntology());
	}

	private final java.util.Set<OWLOntology> visited = new java.util.HashSet<>();

    void reload() { clear(); visited.clear(); loadData(owlEditorKit.getOWLModelManager().getActiveOntology()); }

    private void loadData(OWLOntology owlOntology) {
        if (!visited.add(owlOntology)) return;
		GraphNode parentNode = addOntologyNode(owlOntology);

		for(OWLOntology importedOntology : owlOntology.getDirectImports()) {
			GraphNode childNode = addOntologyNode(importedOntology);
			addArc(parentNode, childNode);

			loadData(importedOntology);
		}
	}

	private GraphNode addOntologyNode(OWLOntology ontology) {
		OWLModelManager modelManager = owlEditorKit.getOWLWorkspace().getOWLModelManager();
		OWLIconProviderImpl iconProvider = new OWLIconProviderImpl(modelManager);
		Icon icon = iconProvider.getIcon(modelManager.getActiveOntology());
		return addNode(ontology, modelManager.getRendering(ontology), icon, ONTOLOGY);
	}

	public Collection<Object> getNodeTypes() {
		ArrayList<Object> types = new ArrayList<Object>(2);
		types.add(ONTOLOGY);

		return types;
	}

	private GraphArc addArc(GraphNode src, GraphNode dest) {
		Object arcId = java.util.Arrays.asList(src.getUserObject(), dest.getUserObject());

		DefaultGraphArc arc = (DefaultGraphArc) addArc(arcId, src, dest, IMPORTS);

		arc.setInverted(false);
		return arc;
	}

	public Collection<Object> getArcTypes() {
		ArrayList<Object> types = new ArrayList<Object>(3);
		types.add(IMPORTS);

		return types;
	}
}
