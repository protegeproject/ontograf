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

		if(result != JFileChooser.APPROVE_OPTION) return;

		File file = fileChooser.getSelectedFile();
		loadFromFile(file);
	}

    public void loadFromFile(File file) {
        try {
            org.protege.ontograf.io.GraphState state = org.protege.ontograf.io.GraphState.read(file.toPath());
            int missing = controller.restoreState(state);
            if (missing > 0) JOptionPane.showMessageDialog(parent, missing + " saved entities could not be found in the active ontology.");
        } catch (java.io.IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Cannot open graph", JOptionPane.ERROR_MESSAGE);
        }
    }
}
