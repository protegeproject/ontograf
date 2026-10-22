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
			try {
            int numOfResults = graphController.search(searchField.getText(), getSearchMode());
			searchResults.setText(numOfResults + " result(s) found.");


            syncNodeSelection();
            } catch (java.util.regex.PatternSyntaxException ex) {
                JOptionPane.showMessageDialog(this, ex.getDescription(), "Invalid regular expression", JOptionPane.WARNING_MESSAGE);
            } finally {
                this.setCursor(Cursor.getDefaultCursor());
            }
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
        toolBar.addSeparator();
        toolBar.add(new JLabel("Labels: "));
        JComboBox<org.protege.ontograf.ui.EdgeLabelMode> labels = new JComboBox<>(org.protege.ontograf.ui.EdgeLabelMode.values());
        // Use the look-and-feel's content size instead of filling spare toolbar space.
        labels.setMaximumSize(labels.getPreferredSize());
        labels.setSelectedItem(graphController.getModel().getLabelMode());
        labels.setToolTipText("<html>All: keep labels visible when selection changes.<br>"
                + "Selected: show labels only for selected nodes or edges; clicking empty canvas hides them.<br>"
                + "Off: hide labels. All modes hide text below 45% zoom.</html>");
        labels.addActionListener(event -> {
            org.protege.ontograf.ui.EdgeLabelMode mode = (org.protege.ontograf.ui.EdgeLabelMode)labels.getSelectedItem();
            if (graphController.getModel().getLabelMode() != mode) graphController.setLabelMode(mode);
        });
        graphController.addLabelModeListener(event -> labels.setSelectedItem(event.getNewValue()));
        toolBar.add(labels);
        javax.swing.JPopupMenu optionsMenu = new javax.swing.JPopupMenu();
        JButton options = new JButton("Graph options");
        options.addActionListener(event -> optionsMenu.show(options, 0, options.getHeight()));
        toolBar.add(options);
        JButton back = new JButton("Back"), forward = new JButton("Forward");
        back.addActionListener(event -> graphController.navigate(true));
        forward.addActionListener(event -> graphController.navigate(false));
        toolBar.add(back); toolBar.add(forward);
        JButton pin = new JButton("Pin nodes");
        pin.setToolTipText("Toggle fixed positions for selected nodes");
        pin.addActionListener(event -> {
            for (GraphNode node : graphController.getGraph().getSelectedNodes()) {
                OWLEntity entity = (OWLEntity)node.getUserObject();
                graphController.getModel().setPinned(entity, !graphController.getModel().getPinnedEntities().contains(entity));
            }
            graphController.refresh();
        });
        optionsMenu.add(pin);
        JButton labelSource = new JButton("Label source");
        labelSource.addActionListener(event -> {
            org.protege.ontograf.common.ProtegeGraphModel model = graphController.getModel();
            JTextField property = new JTextField(model.getLabelAnnotation() == null ? "" : model.getLabelAnnotation().toString(), 38);
            JTextField language = new JTextField(model.getLabelLanguage(), 12);
            JPanel settings = new JPanel(new java.awt.GridLayout(0, 1));
            settings.add(new JLabel("Axiom annotation property IRI (blank uses relationship rendering)")); settings.add(property);
            settings.add(new JLabel("Preferred language")); settings.add(language);
            if (JOptionPane.showConfirmDialog(this, settings, "Edge label source", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                String iri = property.getText().trim();
                if (!iri.isEmpty() && !isAbsoluteIri(iri)) {
                    JOptionPane.showMessageDialog(this, "Enter an absolute annotation property IRI."); return;
                }
                model.setLabelAnnotation(iri.isEmpty() ? null : org.semanticweb.owlapi.model.IRI.create(iri), language.getText().trim());
                graphController.refresh();
            }
        });
        optionsMenu.add(labelSource);
        javax.swing.JSpinner limit = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(graphController.getModel().getExpansionLimit(), 1, 10000, 100));
        limit.setToolTipText("Maximum new edges per expansion; expand again to load more");
        limit.addChangeListener(event -> graphController.getModel().setExpansionLimit((Integer)limit.getValue()));
        optionsMenu.add(new JLabel(" Expansion limit: ")); optionsMenu.add(limit);
        JButton legend = new JButton("Legend");
        legend.addActionListener(event -> JOptionPane.showMessageDialog(this,
                "Solid edges: has subclass / has individual.\nDashed edges: property conditions, assertions or equivalence.\n"
                + "Some/all/min/max/exact/value describe OWL restrictions.\nDomain>Range is a constraint summary, not an instance assertion.\n"
                + "Edges show asserted axioms. Double-click an edge to inspect its supporting axioms.\n"
                + "Labels use Protégé rendering (such as rdfs:label) unless an axiom annotation is selected.\n"
                + "Selected mode shows labels on edges incident to selected nodes. Labels hide below 45% zoom.\n"
                + "Expansion is limited; expand again to show further neighbours.", "OntoGraf legend", JOptionPane.INFORMATION_MESSAGE));
        optionsMenu.add(legend);
        JButton refresh = new JButton("Refresh"), cancel = new JButton("Cancel refresh");
        refresh.addActionListener(event -> graphController.refreshOntology());
        cancel.addActionListener(event -> graphController.cancelRefresh());
        optionsMenu.add(refresh); optionsMenu.add(cancel);

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

    private static boolean isAbsoluteIri(String iri) {
        try { return java.net.URI.create(iri).isAbsolute(); }
        catch (IllegalArgumentException ex) { return false; }
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
        if (graphController != null) graphController.dispose();

	}

}
