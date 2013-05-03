package org.protege.ontograf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.protege.editor.owl.ui.view.cls.AbstractOWLClassViewComponent;
import org.protege.ontograf.actions.ConfigTooltipsAction;
import org.protege.ontograf.actions.ExportAsDotAction;
import org.protege.ontograf.actions.ExportImageAction;
import org.protege.ontograf.actions.OpenGraphAction;
import org.protege.ontograf.actions.PinTooltipsAction;
import org.protege.ontograf.actions.SaveGraphAction;
import org.protege.ontograf.common.GraphController;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;

import ca.uvic.cs.chisel.cajun.graph.AbstractGraph;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNodeCollectionEvent;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNodeCollectionListener;
import ca.uvic.cs.chisel.cajun.util.GradientPainter;
import ca.uvic.cs.chisel.cajun.util.GradientPanel;

/**
 * Plugin extension point for the OntoGraf view.
 * 
 * @author seanf
 */
public class OntoGrafView extends AbstractOWLClassViewComponent  {
	private static final long serialVersionUID = -6969495880634875570L;
	
	private static final Color BACKGROUND_COLOR = new Color(0, 46, 123);

	// search modes available
	private static final String[] MODES = { GraphController.CONTAINS, GraphController.STARTS_WITH, GraphController.ENDS_WITH, GraphController.EXACT_MATCH, GraphController.REGEXP };

	// reference to the graph controller object, gives access to graph model and graph functions
	private GraphController graphController;

	// search related UI controls
	private JComboBox searchTypeBox;
	private JTextField searchField;
	private JLabel searchResults;
	
	// flag for cancelling updateView call, this is for managing synchronization between
	// the graph and the class tree
	private boolean cancelSelectionUpdate;
	
	private JPanel getSearchPanel() {
		JPanel searchPanel = new GradientPanel(GradientPanel.BG_START, BACKGROUND_COLOR.darker(), GradientPainter.TOP_TO_BOTTOM);

		JLabel searchLabel = new JLabel("Search:");
		searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD));
		searchLabel.setForeground(Color.white);

		searchField = new JTextField();
		searchField.setMinimumSize(new Dimension(300, 22));
		searchField.setSize(new Dimension(300, 22));
		searchField.setPreferredSize(new Dimension(300, 22));
		searchField.setFocusable(true);
		searchField.requestFocus();

		searchTypeBox = new JComboBox(MODES);

		JButton searchButton = new JButton("Search");
		searchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				performSearch();
			}
		});
		searchButton.setMinimumSize(new Dimension(80, 22));
		searchButton.setSize(new Dimension(80, 22));
		searchButton.setPreferredSize(new Dimension(80, 22));

		searchField.addKeyListener(new KeyListener() {
			public void keyTyped(KeyEvent e) {}
			public void keyPressed(KeyEvent e) {}

			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					performSearch();
				}
			}
		});

		JButton clearButton = new JButton("Clear");
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				graphController.clear();
				searchResults.setText("");
			}
		});
		clearButton.setMinimumSize(new Dimension(80, 22));
		clearButton.setSize(new Dimension(80, 22));
		clearButton.setPreferredSize(new Dimension(80, 22));

		searchResults = new JLabel();
		searchResults.setFont(searchResults.getFont().deriveFont(Font.BOLD));
		searchResults.setForeground(Color.white);
		searchResults.setOpaque(false);

		searchPanel.add(searchLabel);
		searchPanel.add(searchField);
		searchPanel.add(searchTypeBox);
		searchPanel.add(searchButton);
		searchPanel.add(clearButton);
		searchPanel.add(searchResults);
		
		return searchPanel;
	}

	private void performSearch() {
		if (searchField.getText().length() > 0) {
			this.setCursor(new Cursor(Cursor.WAIT_CURSOR));
			int numOfResults = graphController.search(searchField.getText(), getSearchMode());
			searchResults.setText(numOfResults + " result(s) found.");
			this.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			
			syncNodeSelection();
		} else {
			JOptionPane.showMessageDialog(this, "You must enter a valid search term", "Invalid search term", JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	/**
	 * When a node gets selected in the graph, we want to update the global class selection for Protege.
	 */
	private void syncNodeSelection() {
		GraphNode node = ((AbstractGraph)graphController.getGraph()).getFirstSelectedNode();
		if(node != null) {
			cancelSelectionUpdate = true;
			setGlobalSelection((OWLEntity)node.getUserObject());
		}
	}

	private String getSearchMode() {
		return MODES[searchTypeBox.getSelectedIndex()];
	}

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
		
		graphController = new GraphController(this, this.getOWLEditorKit());
		
		graphController.getGraph().addNodeSelectionListener(new GraphNodeCollectionListener() {
			public void collectionChanged(GraphNodeCollectionEvent arg0) {
				syncNodeSelection();
			}
		});
		
		add(getSearchPanel(), BorderLayout.NORTH);
		
		initToolbar();

		Dimension d = new Dimension(800, 600);
		setPreferredSize(d);
		setSize(d);
		setLocation(100, 50);
		
		setVisible(true);
	}
	
	private void initToolbar() {
		JToolBar toolBar = graphController.getToolBar();
		
		JFrame mainWindow = (javax.swing.JFrame)SwingUtilities.windowForComponent(this);
		
		toolBar.addSeparator();
		toolBar.add(new ExportImageAction(mainWindow, graphController.getGraph().getCanvas()));
		toolBar.add(new ConfigTooltipsAction(mainWindow, graphController.getGraph().getCanvas()));
		toolBar.addSeparator();
		toolBar.add(new SaveGraphAction(mainWindow, graphController));
		toolBar.add(new OpenGraphAction(mainWindow, graphController));
		toolBar.addSeparator();
		toolBar.add(new ExportAsDotAction(mainWindow, graphController));
		
		Action action = new PinTooltipsAction(mainWindow, graphController);
		JToggleButton btn = new JToggleButton(action);
		btn.setText(null);
		btn.setToolTipText((String) action.getValue(Action.NAME));
		
		toolBar.add(btn);
	}
	
	@Override
	protected OWLClass updateView(OWLClass owlClass) {
		if(owlClass != null && !cancelSelectionUpdate) {
			graphController.showOWLClass(owlClass);
		}
		
		cancelSelectionUpdate = false;
		
		return null;
	}

	@Override
	public void disposeView() {
		// TODO Auto-generated method stub
		
	}

}
