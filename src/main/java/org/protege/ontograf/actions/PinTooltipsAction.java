package org.protege.ontograf.actions;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.protege.ontograf.common.GraphController;
import org.protege.ontograf.common.util.IconConstants;
import org.protege.ontograf.ui.FrameTooltipNode;
import org.semanticweb.owlapi.model.OWLEntity;

import ca.uvic.cs.chisel.cajun.actions.CajunAction;
import ca.uvic.cs.chisel.cajun.graph.AbstractGraph;
import ca.uvic.cs.chisel.cajun.graph.GraphModelAdapter;
import ca.uvic.cs.chisel.cajun.graph.node.DefaultGraphNode;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import edu.umd.cs.piccolo.PNode;

public class PinTooltipsAction extends CajunAction {
	private static final long serialVersionUID = 7241297162054742885L;

	private static final String ACTION_NAME = "Pin Node Tooltips";
	
	private GraphController graphController;
	
	private boolean show = true;
	
	private Map<PNode, PNode> existingTooltipsMap;
	private Map<PNode, ChangeListener> changeListenerMap;
	
	public PinTooltipsAction(Component parent, GraphController graphController) {
		super(ACTION_NAME, IconConstants.ICON_PIN_TOOLTIPS);
		
		putValue(Action.SHORT_DESCRIPTION, ACTION_NAME);
		
		this.existingTooltipsMap = new HashMap<PNode, PNode>();
		this.changeListenerMap = new HashMap<PNode, ChangeListener>();
		
		this.graphController = graphController;
		
		graphController.getModel().addGraphModelListener(new GraphModelAdapter() {
			@Override
			public void graphNodeAdded(GraphNode arg0) {
				if(!show) {
					showAllTooltips();
				}
			}
		});
	}
	
	public void doAction() {
		if(show) {
			showAllTooltips();
			show = false;
		}
		else {
			hideAllTooltips();
			show = true;
		}
	}
	
	private void hideAllTooltips() {
		for(Entry<PNode, PNode> entry : existingTooltipsMap.entrySet()) {
			entry.getKey().removeChild(entry.getValue());
			
			ChangeListener changeListener = changeListenerMap.get(entry.getValue());
			((DefaultGraphNode)entry.getKey()).removeChangeListener(changeListener);
			
			entry.getKey().repaint();
		}
		
		existingTooltipsMap.clear();
		changeListenerMap.clear();
	}
	
	private void showAllTooltips() {
		for(GraphNode node : graphController.getModel().getAllNodes()) {
			// make sure there isn't already a tooltip showing
			if(existingTooltipsMap.get((PNode)node) == null) {
				FrameTooltipNode toolTip = new FrameTooltipNode(graphController.getModel().getOwlModelManager(), (AbstractGraph)graphController.getGraph(), (PNode)node, (OWLEntity)node.getUserObject());
				((PNode)node).addChild(toolTip);
			
				existingTooltipsMap.put((PNode)node, toolTip);
				
				ChangeListener changeListener = new ChangeListener() {
					public void stateChanged(ChangeEvent e) {
						PNode targetNode = existingTooltipsMap.get((PNode)e.getSource());
						if(targetNode != null) {
							FrameTooltipNode toolTip = (FrameTooltipNode)targetNode;
							toolTip.updateLocation((AbstractGraph)graphController.getGraph(), toolTip.getParent());
						}
					}
				};
				
				((DefaultGraphNode)node).addChangeListener(changeListener);
				changeListenerMap.put(toolTip, changeListener);
			}
		}
	}
}
