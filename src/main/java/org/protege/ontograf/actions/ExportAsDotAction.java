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

		if(result != JFileChooser.APPROVE_OPTION) return;

		String filePath = fileChooser.getSelectedFile().getPath();
		if(!filePath.toLowerCase(java.util.Locale.ROOT).endsWith(".dot")) {
			filePath += ".dot";
		}
		File file = new File(filePath);

		if(file.exists()) {
			String msg = "The file " + file.toString() + " already exists.\nDo you want to overwrite this existing file?";
			int opt = JOptionPane.showConfirmDialog(parent, msg,
					"Export Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

			if(opt != JOptionPane.YES_OPTION) return;
		}

        try (java.io.Writer out = java.nio.file.Files.newBufferedWriter(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            org.protege.ontograf.io.DotExporter.write(controller.getModel(), out);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Cannot export graph", JOptionPane.ERROR_MESSAGE);
        }
    }
}
