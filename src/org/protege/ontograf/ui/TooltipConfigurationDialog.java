package org.protege.ontograf.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.border.TitledBorder;

import org.protege.ontograf.common.util.NodeOWLClassTooltipType;
import org.protege.ontograf.common.util.NodeOWLIndividualTooltipType;

import ca.uvic.cs.chisel.cajun.graph.ui.FilterCheckBox;

public class TooltipConfigurationDialog extends JDialog {
	private static final long serialVersionUID = -5081919405096749260L;
	
	private Map<String, Boolean> owlClassTooltipTypes;
	private Map<String, Boolean> owlIndividualTooltipTypes;
	
	public TooltipConfigurationDialog() {
		setSize(600, 300);
		
		owlClassTooltipTypes = new HashMap<String, Boolean>();
		for(NodeOWLClassTooltipType tooltipType : NodeOWLClassTooltipType.values()) {
			owlClassTooltipTypes.put(tooltipType.toString(), tooltipType.isEnabled());
		}
		
		owlIndividualTooltipTypes = new HashMap<String, Boolean>();
		for(NodeOWLIndividualTooltipType tooltipType : NodeOWLIndividualTooltipType.values()) {
			owlIndividualTooltipTypes.put(tooltipType.toString(), tooltipType.isEnabled());
		}
		
		init();
	}
	
	private void init() {
		setLayout(new BorderLayout());
		setTitle("Node tooltip configuration");
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		
		splitPane.add(getTypeScrollPanel(getTypesPanel("Class Tooltip Options:"), owlClassTooltipTypes));
		splitPane.add(getTypeScrollPanel(getTypesPanel("Individual Tooltip Options:"), owlIndividualTooltipTypes));
		
		splitPane.setDividerLocation(300);
		
		add(splitPane, BorderLayout.CENTER);
		add(getButtonPanel(), BorderLayout.SOUTH);
	}
	
	private JScrollPane getTypeScrollPanel(JPanel typesPanel, Map<String, Boolean> types) {
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		holder.add(typesPanel, BorderLayout.NORTH);
		holder.setBackground(Color.white);
		//add(getHeaderPanel(), BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		loadTypes(typesPanel, types);
		
		return scroll;
	}
	
	private JPanel getButtonPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				TooltipConfigurationDialog.this.setVisible(false);
			}
		});
		
		JPanel buttonContainerPanel = new JPanel();
		buttonContainerPanel.add(closeButton);
		
		panel.add(buttonContainerPanel, BorderLayout.EAST);
		
		return panel;
	}
	
	private void typeVisibilityChanged(Object type, boolean visible) {
		for(NodeOWLClassTooltipType tooltipType : NodeOWLClassTooltipType.values()) {
			if(tooltipType.toString().equals(type.toString())) {
				tooltipType.setEnabled(visible);
				break;
			}
		}
	}
	
	private void typeIndividualVisibilityChanged(Object type, boolean visible) {
		for(NodeOWLIndividualTooltipType tooltipType : NodeOWLIndividualTooltipType.values()) {
			if(tooltipType.toString().equals(type.toString())) {
				tooltipType.setEnabled(visible);
				break;
			}
		}
	}
	
	public void loadTypes(final JPanel panel, Map<String, Boolean> items) {
		panel.removeAll();
		if (items.size() > 0) {
			// sort the types alphabetically
			TreeSet<String> sortedTypes = new TreeSet<String>();
			sortedTypes.addAll(items.keySet());
			for (Object type : sortedTypes) {
				boolean selected = items.get(type);
				FilterCheckBox checkbox = new FilterCheckBox(type, null, selected){
					private static final long serialVersionUID = -861175558062891232L;

					public void typeVisibilityChanged(Object type, boolean visible) {
						String title = ((TitledBorder)panel.getBorder()).getTitle();
						if(title.equals("Class Tooltip Options:")) {
							TooltipConfigurationDialog.this.typeVisibilityChanged(type, visible);
						}
						else {
							typeIndividualVisibilityChanged(type, visible);
						}
					}
				};

				panel.add(checkbox);
			}
		}
		this.invalidate();
		this.validate();
		this.repaint();
	}

	private JPanel getTypesPanel(String title) {
		//if (typesPanel == null) {
		
		JPanel	typesPanel = new JPanel(new GridLayout(0, 1, 0, 1));
		typesPanel.setBorder(BorderFactory.createTitledBorder(title));
			typesPanel.setOpaque(false);
		//}
		return typesPanel;
	}
}
