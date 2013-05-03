package org.protege.ontograf.actions;

import java.awt.Component;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.protege.ontograf.common.GraphController;
import org.protege.ontograf.common.util.IconConstants;
import org.protege.ontograf.ui.OntoGrafFileFilter;

import ca.uvic.cs.chisel.cajun.actions.CajunAction;
import ca.uvic.cs.chisel.cajun.graph.GraphModel;
import ca.uvic.cs.chisel.cajun.graph.arc.DefaultGraphArc;
import ca.uvic.cs.chisel.cajun.graph.arc.GraphArc;

public class ExportAsDotAction extends CajunAction {
	private static final long serialVersionUID = 7241297162054742885L;

	private static final String ACTION_NAME = "Export Graph to DOT";
	
	private JFileChooser fileChooser;
	
	private Component parent;
	private GraphController controller;
	
	public ExportAsDotAction(Component parent, GraphController controller) {
		super(ACTION_NAME, IconConstants.ICON_EXPORT_DOT_GRAPH);
		
		putValue(Action.SHORT_DESCRIPTION, ACTION_NAME);
		
		this.parent = parent;
		this.controller = controller;
		
		fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Save graph as DOT file");
		fileChooser.addChoosableFileFilter(new OntoGrafFileFilter(new String[] { "dot" }, "DOT File"));
	}
	
	@Override
	public void doAction() {
		int result = fileChooser.showSaveDialog(parent);
		
		if(result == JFileChooser.CANCEL_OPTION) return;
		
		String filePath = fileChooser.getSelectedFile().getPath();
		if(!filePath.contains(".dot")) {
			filePath += ".dot";
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
			
			out.println("digraph g {");
			//out.println("\tnode [shape=");
			
			for(GraphArc arc : model.getAllArcs()) {
				if(arc instanceof DefaultGraphArc) {
					DefaultGraphArc graphArc = (DefaultGraphArc)arc;
					out.println("\t\"" + graphArc.getSource().getText() + "\" -> \"" + graphArc.getDestination().getText()
							+ "\" [label=\"" + graphArc.getType() + "\"]");
				}
			}
			
//			for(GraphNode node : model.getAllNodes()) {
//				if(node instanceof DefaultGraphNode) {
//					DefaultGraphNode graphNode = (DefaultGraphNode)node;
//					graphNode.
//					
//					//OWLEntity entity = (OWLEntity)graphNode.getUserObject();
//					
//					//out.println(entity.getIRI().toString() + "," + graphNode.getX() + "," + graphNode.getY());
//				}
//			}
			out.println("}");
			out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
