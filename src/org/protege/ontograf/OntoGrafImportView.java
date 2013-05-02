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
		// TODO Auto-generated method stub
		
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
	
	private void loadData(OWLOntology owlOntology) {
		GraphNode parentNode = addNode(owlEditorKit.getOWLWorkspace().getOWLModelManager().getRendering(owlOntology));
		
		for(OWLOntology importedOntology : owlOntology.getDirectImports()) {
			GraphNode childNode = addNode(owlEditorKit.getOWLWorkspace().getOWLModelManager().getRendering(importedOntology));
			addArc(parentNode, childNode);
			
			loadData(importedOntology);
		}
	}
	
	private GraphNode addNode(String name) {
		OWLModelManager modelManager = owlEditorKit.getOWLWorkspace().getOWLModelManager();
		OWLIconProviderImpl iconProvider = new OWLIconProviderImpl(modelManager);
		Icon icon = iconProvider.getIcon(modelManager.getActiveOntology());
		return addNode(name, name, icon, ONTOLOGY);
	}

	public Collection<Object> getNodeTypes() {
		ArrayList<Object> types = new ArrayList<Object>(2);
		types.add(ONTOLOGY);
		
		return types;
	}
	
	private GraphArc addArc(GraphNode src, GraphNode dest) {
		String arcId = src.getText() + "->" + dest.getText();
		
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
