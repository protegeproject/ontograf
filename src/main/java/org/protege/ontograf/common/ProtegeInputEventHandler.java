package org.protege.ontograf.common;

import org.protege.ontograf.ui.FrameTooltipNode;
import org.semanticweb.owlapi.model.OWLEntity;

import ca.uvic.cs.chisel.cajun.graph.AbstractGraph;
import ca.uvic.cs.chisel.cajun.graph.node.DefaultGraphNode;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.event.PBasicInputEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.event.PInputEventFilter;

/**
 * Handles input events on the graph.
 * 
 * @author seanf
 */
public class ProtegeInputEventHandler extends PBasicInputEventHandler {
	private static final int DOUBLE_CLICK = 2;

	private ProtegeGraphModel graphModel;
	private AbstractGraph graph;
	
	private FrameTooltipNode toolTip;
	private DefaultGraphNode currentNode;

	public ProtegeInputEventHandler(ProtegeGraphModel graphModel, AbstractGraph graph) {
		this.graphModel = graphModel;
		this.graph = graph;

		PInputEventFilter filter = new PInputEventFilter();
		filter.rejectAllEventTypes();
		filter.setAcceptsMousePressed(true);
		filter.setAcceptsMouseMoved(true);
		
		this.setEventFilter(filter);
	}
	
	public void mouseMoved(PInputEvent event) {
		if(event.getPickedNode() instanceof GraphNode) {
			if(!event.getPickedNode().equals(currentNode)) {
				showToolTip((DefaultGraphNode)event.getPickedNode());
			}
		}
		else if(currentNode != null) {
			hideToolTip(currentNode);
			currentNode = null;
		}
	}

	public void mousePressed(PInputEvent event) {
		hideToolTip(currentNode);
		
		if (event.isLeftMouseButton()) {
			if (event.getClickCount() == DOUBLE_CLICK) {
				if (event.getPickedNode() instanceof GraphNode) {
					expandCollapseNode((GraphNode) event.getPickedNode());
					//((FlatGraph) graph).getAnimationHandler().moveViewToCenterBounds(graph.getBounds(), false, 1000, true);
				}
			}
			else if(event.getClickCount() == 1 && event.isControlDown()) {
				showToolTip((DefaultGraphNode)event.getPickedNode());
				currentNode = null;
			}
		}
	}
	
	private void hideToolTip(DefaultGraphNode node) {
		PCamera camera = graph.getCanvas().getCamera();
		if(toolTip != null) {
			camera.removeChild(toolTip);
			camera.repaint();
			toolTip = null;
		}
	}
	
	private void showToolTip(DefaultGraphNode node) {
		PCamera camera = graph.getCanvas().getCamera();
		hideToolTip(node);
		
		toolTip = new FrameTooltipNode(graphModel.getOwlModelManager(), graph, node, (OWLEntity)node.getUserObject());
		camera.addChild(toolTip);
		camera.repaint();
		
		currentNode = node;
	}
	
	/**
	 * Expands a node if it is not already expanded, otherwise it collapses it.
	 * 
	 * @param graphNode The node to expand or collapse.
	 */
	private void expandCollapseNode(GraphNode graphNode) {
		graphNode.setHighlighted(false);
		graphNode.moveToFront();

		if (graphModel.isExpanded(graphNode)) {
			graphModel.collapseNode(graphNode);
		} else {
			graphModel.expandNode(graphNode);
		}

		graph.performLayout();
	}

}
