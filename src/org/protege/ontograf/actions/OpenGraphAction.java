package org.protege.ontograf.actions;

import java.awt.Component;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Set;

import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.protege.editor.owl.model.OWLModelManager;
import org.protege.ontograf.common.GraphController;
import org.protege.ontograf.common.ProtegeGraphModel;
import org.protege.ontograf.common.util.IconConstants;
import org.protege.ontograf.ui.OntoGrafFileFilter;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;

import ca.uvic.cs.chisel.cajun.actions.CajunAction;
import ca.uvic.cs.chisel.cajun.graph.FlatGraph;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;

public class OpenGraphAction extends CajunAction {
	private static final long serialVersionUID = 7241297162054742885L;

	private static final String ACTION_NAME = "Open Saved Graph";
	
	private JFileChooser fileChooser;
	
	private Component parent;
	private GraphController controller;
	
	public OpenGraphAction(Component parent, GraphController controller) {
		super(ACTION_NAME, IconConstants.ICON_OPEN_GRAPH);
		
		putValue(Action.SHORT_DESCRIPTION, ACTION_NAME);
		
		this.parent = parent;
		this.controller = controller;
		
		fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Open graph configuration file");
		fileChooser.addChoosableFileFilter(new OntoGrafFileFilter(new String[] { "graph" }, "OntoGraf File"));
	}
	
	@Override
	public void doAction() {
		int result = fileChooser.showOpenDialog(parent);
		
		if(result == JFileChooser.CANCEL_OPTION) return;
		
		File file = fileChooser.getSelectedFile();
		loadFromFile(file);
	}
	
	public void loadFromFile(File file) {
		controller.clear();
		
		ProtegeGraphModel model = controller.getModel();
		FlatGraph graph = (FlatGraph)controller.getGraph();
		OWLModelManager owlModelManager = controller.getModel().getOwlModelManager();
		boolean found = true;
		
		try {
			Scanner scanner = new Scanner(file);
			
			// make sure the stored graph is for the active ontology
			if(scanner.hasNextLine()) {
				String ontologyUri = scanner.nextLine();
				String activeOntologyUri = owlModelManager.getActiveOntology().getOntologyID().getOntologyIRI().toString();
				
				if(!ontologyUri.equals(activeOntologyUri)) {
					JOptionPane.showMessageDialog(parent, "Sorry, but the graph description does not correspond to your active ontology.");
					return;
				}
			}
			
			// attempt to read the csv file and display the elements
			while(scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String items[] = line.split(",");
				if(items.length == 3) {
					String uri = items[0];
					double x = Double.parseDouble(items[1]); 
					double y = Double.parseDouble(items[2]); 
					
					Set<OWLEntity> entities = owlModelManager.getOWLEntityFinder().getEntities(IRI.create(uri));
					if(entities != null && entities.size() > 0) {
						for(OWLEntity owlEntity : entities) {
							model.show(owlEntity, graph.getFilterManager());
							
							GraphNode node = model.getNode(owlEntity);
							node.setLocation(x, y);
						}
					}
					else {
						found = false;
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		if(!found) {
			JOptionPane.showMessageDialog(parent, "Some of the graph entities in the file could not be found within your currenlty loaded ontology.\nPlease make sure this saved graph corresponds to your active ontology.");
		}
	}
}
