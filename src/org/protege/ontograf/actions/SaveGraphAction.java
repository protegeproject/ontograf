package org.protege.ontograf.actions;

import java.awt.Component;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.protege.editor.owl.model.OWLModelManager;
import org.protege.ontograf.common.GraphController;
import org.protege.ontograf.common.util.IconConstants;
import org.protege.ontograf.ui.OntoGrafFileFilter;
import org.semanticweb.owlapi.model.OWLEntity;

import ca.uvic.cs.chisel.cajun.actions.CajunAction;
import ca.uvic.cs.chisel.cajun.graph.GraphModel;
import ca.uvic.cs.chisel.cajun.graph.node.DefaultGraphNode;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;

public class SaveGraphAction extends CajunAction {
	private static final long serialVersionUID = 7241297162054742885L;

	private static final String ACTION_NAME = "Save Current Graph";
	
	private JFileChooser fileChooser;
	
	private Component parent;
	private GraphController controller;
	
	public SaveGraphAction(Component parent, GraphController controller) {
		super(ACTION_NAME, IconConstants.ICON_SAVE_GRAPH);
		
		putValue(Action.SHORT_DESCRIPTION, ACTION_NAME);
		
		this.parent = parent;
		this.controller = controller;
		
		fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Save graph configuration to a file");
		fileChooser.addChoosableFileFilter(new OntoGrafFileFilter(new String[] { "graph" }, "OntoGraf File"));
	}
	
	@Override
	public void doAction() {
		int result = fileChooser.showSaveDialog(parent);
		
		if(result == JFileChooser.CANCEL_OPTION) return;
		
		String filePath = fileChooser.getSelectedFile().getPath();
		if(!filePath.contains(".graph")) {
			filePath += ".graph";
		}
		File file = new File(filePath);
		
		if(file.exists()) {
			String msg = "The file " + file.toString() + " already exists.\nDo you want to overwrite this existing file?";
			int opt = JOptionPane.showConfirmDialog(parent, msg,
					"Export Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			
			if(opt == JOptionPane.NO_OPTION) return;
		}
		
		try {
			PrintStream out = new PrintStream(file);
			
			GraphModel model = controller.getGraph().getModel();
			OWLModelManager owlModelManager = controller.getModel().getOwlModelManager();
			
			out.println(owlModelManager.getActiveOntology().getOntologyID().getOntologyIRI().toString());
			
			for(GraphNode node : model.getAllNodes()) {
				if(node instanceof DefaultGraphNode) {
					DefaultGraphNode graphNode = (DefaultGraphNode)node;
					OWLEntity entity = (OWLEntity)graphNode.getUserObject();
					
					out.println(entity.getIRI().toString() + "," + graphNode.getX() + "," + graphNode.getY());
				}
			}
			out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
