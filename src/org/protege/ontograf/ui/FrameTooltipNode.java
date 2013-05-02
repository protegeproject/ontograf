package org.protege.ontograf.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.protege.editor.owl.model.OWLModelManager;
import org.protege.ontograf.common.util.NodeOWLClassTooltipType;
import org.protege.ontograf.common.util.NodeOWLIndividualTooltipType;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import ca.uvic.cs.chisel.cajun.graph.AbstractGraph;
import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PPaintContext;
import edu.umd.cs.piccolox.util.PFixedWidthStroke;

public class FrameTooltipNode extends PNode {
	private static final long serialVersionUID = 1171088034485980559L;
	
	private static final Color BACKGROUND_COLOR = new Color(255, 255, 203);
	
	private static final int PADDING_X = 12;
	private static final int PADDING_Y = 6;
	protected static final int MAX_TEXT_CHARS = 75;
	protected static final int MAX_LINES = 3;

	private List<GraphTextNode> textNodes;
	
	protected Stroke borderStroke;
	
	private OWLModelManager owlModelManager;
	private OWLEntity owlEntity;
	
	private boolean isCameraNode;
	
	public FrameTooltipNode(OWLModelManager owlModelManager, AbstractGraph graph, PNode owner, OWLEntity owlEntity) {
		this.borderStroke = new PFixedWidthStroke(1f);
		
		this.owlModelManager = owlModelManager;
		this.owlEntity = owlEntity;
		
		isCameraNode = true;
		
		initLocation(graph, owner);
		initText();
		adjustX(graph, owner);
		
		repaint();
	}
	
	public void updateLocation(AbstractGraph graph, PNode owner) {
		this.removeAllChildren();
		
		isCameraNode = false;
		
		initLocation(graph, owner);
		initText();
		adjustX(graph, owner);
		
		repaint();
	}
	
	public void setText(GraphTextNode textNode, String s) {
		if (s == null) {
			s = "";
		}

		textNode.setText(splitTextIntoLines(s, MAX_LINES, MAX_TEXT_CHARS));
	}
	
	/**
	 * Moves the tooltip to the left if the owner is on the right hand side
	 * of the screen.
	 */
	private void adjustX(AbstractGraph graph, PNode owner) {
		Rectangle2D rect = owner.getBounds();
		if(isCameraNode) rect = graph.getCamera().viewToLocal(owner.getBounds());
		
		double maxX = graph.getWidth() / 3;
		maxX *= 2;
		if(getX() > maxX) {
			this.translate(-1 * (getWidth() + rect.getWidth()), 0);
		}
	}
	
	private void initText() {
		double x = getX() + 5, y = getY() + 5;
		
		textNodes = new ArrayList<GraphTextNode>();
		
		Font font = PText.DEFAULT_FONT.deriveFont(10f);
		
		Point2D currentPos = new Point2D.Double(x, y);
		
		if(owlEntity instanceof OWLClass) {
			currentPos = initClassTooltips(currentPos, font);
		}
		else if(owlEntity instanceof OWLIndividual) {
			currentPos = initIndividualTooltips(currentPos, font);
		}
		
		updateBounds();
	}
	
	private Point2D createTitleText(Point2D currentPos, Font font) {
		textNodes.add(createTextNode(currentPos.getX(), currentPos.getY(), owlModelManager.getRendering(owlEntity), font));
		double y = currentPos.getY() + textNodes.get(textNodes.size()-1).getHeight() + 2;
		
		return new Point2D.Double(currentPos.getX(), y);
	}
	
	private Point2D createURIText(Point2D currentPos, Font font) {
		double x = currentPos.getX();
		double y = currentPos.getY();
		textNodes.add(createTextNode(x, y, "URI:", font.deriveFont(Font.BOLD)));
		x += textNodes.get(textNodes.size()-1).getWidth() + 5; 
		textNodes.add(createTextNode(x, y, owlEntity.getIRI().toString(), font));
		
		y += textNodes.get(textNodes.size()-1).getHeight() + 2;
		x = textNodes.get(0).getX();
		
		return new Point2D.Double(x, y);
	}
	
	private Point2D initIndividualTooltips(Point2D currentPos, Font font) {
		OWLIndividual individual = (OWLIndividual)owlEntity;
		
		if(NodeOWLIndividualTooltipType.TITLE.isEnabled()) {
			currentPos = createTitleText(currentPos, font);
		}
		
		if(NodeOWLIndividualTooltipType.URI.isEnabled()) {
			currentPos = createURIText(currentPos, font);
		}
		
		if(NodeOWLIndividualTooltipType.DIFFERENT_INDIVIDUALS.isEnabled()) {
			Set<OWLIndividual> individuals = individual.getDifferentIndividuals(owlModelManager.getActiveOntology());
			if(individuals.size() > 0) {
				currentPos = addCollectionTextValues(individuals, "Different individuals:", font, currentPos);
			}
		}
		
		if(NodeOWLIndividualTooltipType.SAME_INDIVIDUALS.isEnabled()) {
			Set<OWLIndividual> individuals = individual.getSameIndividuals(owlModelManager.getActiveOntology());
			if(individuals.size() > 0) {
				currentPos = addCollectionTextValues(individuals, "Same individuals:", font, currentPos);
			}
		}
		
		if(NodeOWLIndividualTooltipType.OBJECT_PROPERTY_ASSERTIONS.isEnabled()) {
			Set<OWLObjectPropertyAssertionAxiom> properties = owlModelManager.getActiveOntology().getObjectPropertyAssertionAxioms(individual);
			if(properties.size() > 0) {
				currentPos = addCollectionTextValues(properties, "Object property assertions:", font, currentPos);
			}
		}
		
		if(NodeOWLIndividualTooltipType.DATA_PROPERTY_ASSERTIONS.isEnabled()) {
			Set<OWLDataPropertyAssertionAxiom> dataProperties = owlModelManager.getActiveOntology().getDataPropertyAssertionAxioms(individual);
			if(dataProperties.size() > 0) {
				currentPos = addCollectionTextValues(dataProperties, "Data property assertions:", font, currentPos);
			}
		}
		
		if(NodeOWLIndividualTooltipType.NEGATIVE_DATA_PROPERTY_ASSERTIONS.isEnabled()) {
			Map<OWLDataPropertyExpression, Set<OWLLiteral>> map = individual.getNegativeDataPropertyValues(owlModelManager.getActiveOntology());
			Set<String> negativeDataProperties = new HashSet<String>();
			for(Entry<OWLDataPropertyExpression, Set<OWLLiteral>> entry : map.entrySet()) {
				String property = owlModelManager.getRendering(entry.getKey());
				for(OWLLiteral literal : entry.getValue()) {
					property += " " + owlModelManager.getRendering(literal);
				}
				negativeDataProperties.add(property);
			}
			
			if(negativeDataProperties.size() > 0) {
				currentPos = addStringValues(negativeDataProperties, "Negative data property assertions:", font, currentPos);
			}
		}
		
		
		if(NodeOWLIndividualTooltipType.NEGATIVE_OBJECT_PROPERTY_ASSERTIONS.isEnabled()) {
			Map<OWLObjectPropertyExpression, Set<OWLIndividual>> negativeObjectPropertiesMap = individual.getNegativeObjectPropertyValues(owlModelManager.getActiveOntology());
			Set<String> negativeObjectProperties = new HashSet<String>();
			for(Entry<OWLObjectPropertyExpression, Set<OWLIndividual>> entry : negativeObjectPropertiesMap.entrySet()) {
				String property = owlModelManager.getRendering(entry.getKey());
				for(OWLIndividual owlIndividual : entry.getValue()) {
					property += " " + owlModelManager.getRendering(owlIndividual);
				}
				negativeObjectProperties.add(property);
			}
			
			if(negativeObjectProperties.size() > 0) {
				currentPos = addStringValues(negativeObjectProperties, "Negative object property assertions:", font, currentPos);
			}
		}
		
		if(NodeOWLIndividualTooltipType.ANNOTATIONS.isEnabled()) {
			Set<OWLAnnotation> annotations = owlEntity.getAnnotations(owlModelManager.getActiveOntology());
			if(annotations.size() > 0) {
				currentPos = addCollectionTextValues(annotations, "Annotations:", font, currentPos);
			}
		}
		
		return currentPos;
	}
	
	private Point2D initClassTooltips(Point2D currentPos, Font font) {
		if(NodeOWLClassTooltipType.TITLE.isEnabled()) {
			currentPos = createTitleText(currentPos, font);
		}
		
		if(NodeOWLClassTooltipType.URI.isEnabled()) {
			currentPos = createURIText(currentPos, font);
		}
		
		if(NodeOWLClassTooltipType.SUPERCLASSES.isEnabled()) {
			Set<OWLSubClassOfAxiom> superClasses = owlModelManager.getActiveOntology().getSubClassAxiomsForSubClass((OWLClass)owlEntity);
			if(superClasses.size() > 0) {
				currentPos = addCollectionTextValues(superClasses, "Superclasses:", font, currentPos);
			}
		}
		
		if(NodeOWLClassTooltipType.EQUIVALENT_CLASSES.isEnabled()) {
			Set<OWLEquivalentClassesAxiom> equivalentAxioms = owlModelManager.getActiveOntology().getEquivalentClassesAxioms((OWLClass)owlEntity);
			if(equivalentAxioms.size() > 0) {
				currentPos = addCollectionTextValues(equivalentAxioms, "Equivalent classes:", font, currentPos);
			}
		}
		
		if(NodeOWLClassTooltipType.DISJOINT_CLASSES.isEnabled()) {
			Set<OWLDisjointClassesAxiom> disjointAxioms = owlModelManager.getActiveOntology().getDisjointClassesAxioms((OWLClass)owlEntity);
			if(disjointAxioms.size() > 0) {
				currentPos = addCollectionTextValues(disjointAxioms, "Disjoint classes:", font, currentPos);
			}
		}
		
		if(NodeOWLClassTooltipType.ANNOTATIONS.isEnabled()) {
			Set<OWLAnnotation> annotations = owlEntity.getAnnotations(owlModelManager.getActiveOntology());
			if(annotations.size() > 0) {
				currentPos = addCollectionTextValues(annotations, "Annotations:", font, currentPos);
			}
		}
		
		return currentPos;
	}
	
	private Point2D addStringValues(Set<String> entities, String title, Font font, Point2D point) {
		double x = point.getX();
		double y = point.getY();
		textNodes.add(createTextNode(x, y, title, font.deriveFont(Font.BOLD)));
		y += textNodes.get(textNodes.size()-1).getHeight() + 2;
		x += 10;
		for(String entry : entities) {
			textNodes.add(createTextNode(x, y, entry, font));
			y += textNodes.get(textNodes.size()-1).getHeight() + 2;
		}
		
		return new Point2D.Double(x-10, y);
	}
	
	private Point2D addCollectionTextValues(Set<? extends OWLObject> entities, String title, Font font, Point2D point) {
		double x = point.getX();
		double y = point.getY();
		textNodes.add(createTextNode(x, y, title, font.deriveFont(Font.BOLD)));
		y += textNodes.get(textNodes.size()-1).getHeight() + 2;
		x += 10;
		for(OWLObject annotation : entities) {
			textNodes.add(createTextNode(x, y, owlModelManager.getRendering(annotation), font));
			y += textNodes.get(textNodes.size()-1).getHeight() + 2;
		}
		
		return new Point2D.Double(x-10, y);
	}
	
	private GraphTextNode createTextNode(double x, double y, String text, Font font) {
		GraphTextNode textNode = new GraphTextNode();
		// make this node match the text size
		textNode.setConstrainWidthToTextWidth(true);
		textNode.setConstrainHeightToTextHeight(true);
		textNode.setPickable(false);
		textNode.setFont(font);
		addChild(textNode);
		setText(textNode, text);
		textNode.setX(x);
		textNode.setY(y);
		
		return textNode;
	}
	
	/**
	 * Sets the tooltip location relative the owner's position in the global coordinate system.
	 */
	private void initLocation(AbstractGraph graph, PNode owner) {
		PCamera camera = graph.getCanvas().getCamera();
		Rectangle bounds = graph.getCanvas().getBounds();
		
		Rectangle2D rect = owner.getBounds(); //camera.viewToLocal(owner.getBounds());
		if(isCameraNode) rect = camera.viewToLocal(owner.getBounds());
		
		double x = rect.getX();
		double y = rect.getY();
		
		x += rect.getWidth();
		x = Math.min(x, bounds.getWidth() - this.getWidth());
		
		y = Math.max(y, 0);
		
		this.setX(x);
		this.setY(y);
	}
	
	/**
	 * Restricts the number of characters in the text node. If the string is too long it is chopped
	 * and appended with "...".
	 * 
	 * @param text the string to possibly elide
	 * @return the elided string, or the original if text isn't longer than the max allowed chars
	 */
	protected String elideText(String text, int maxCharsPerLine) {
		if (text.length() > maxCharsPerLine) {
			return new String(text.substring(0, maxCharsPerLine).trim() + "...");
		}
		return text;
	}
	
	/**
	 * Splits the text into lines. Attempts to split at word breaks if possible. Also puts a cap on
	 * the max number of lines.
	 */
	protected String splitTextIntoLines(String text, int maxLines, int maxCharsPerLine) {
		text = text.trim();
		StringBuffer buffer = new StringBuffer(text.length() + 10);
		if (text.length() > maxCharsPerLine) {
			int lines = 0;
			while ((text.length() > 0) && (lines < maxLines)) {
				// base case #1 - text is short
				if (text.length() < maxCharsPerLine) {
					buffer.append(text);
					break;
				}
				// base case #2 - added max lines
				if ((lines + 1) == maxLines) {
					// elide the remaining text (s) instead of just the current line
					buffer.append(elideText(text, maxCharsPerLine));
					break;
				}

				// find a space and break on it
				int end = findWhiteSpace(text, maxCharsPerLine);
				if (end == -1) {
					end = Math.min(text.length(), maxCharsPerLine);
				}
				String line = text.substring(0, end).trim();
				if (line.length() == 0) {
					break;
				}

				buffer.append(line);
				buffer.append('\n');
				lines++;
				text = text.substring(end).trim();
			}
			return buffer.toString().trim();
		}
		return text;
	}

	private int findWhiteSpace(String s, int end) {
		int ws = -1;
		// look 2 characters past the end for a space character
		// and work backwards
		for (int i = Math.min(s.length() - 1, end + 2); i >= 0; i--) {
			if (Character.isWhitespace(s.charAt(i))) {
				ws = i;
				break;
			}
		}
		return ws;
	}
	
	/**
	 * Sets the bounds of this node based on the text size. Takes into consideration the
	 * maximum node width too.
	 */
	private void updateBounds() {
		double w = 0, minY = Double.MAX_VALUE, maxY = 0;
		double h = 0;
		for(GraphTextNode textNode : textNodes) {
			PBounds textBounds = textNode.getBounds();
			w = Math.max(w, (3 * PADDING_X) + textBounds.getWidth());
			minY = Math.min(minY, textBounds.getY());
			if(maxY < textBounds.getY()) {
				maxY = textBounds.getY();
				h = textBounds.getHeight();
			}
			maxY = Math.max(maxY, textBounds.getY());
		}
		
		h += (maxY - minY) + PADDING_Y;
		setBounds(getX(), getY(), w, h);
	}
	
	@Override
	public boolean setBounds(double x, double y, double width, double height) {
		// TODO handle maximum width?
		boolean changed = super.setBounds(x, y, width, height);

		if (changed) {
			invalidatePaint();
		}
		return changed;
	}
	
	private static Color getMixedColor(Color c1, float pct1, Color c2, float pct2) {
	    float[] clr1 = c1.getComponents(null);
	    float[] clr2 = c2.getComponents(null);
	    for (int i = 0; i < clr1.length; i++) {
	        clr1[i] = (clr1[i] * pct1) + (clr2[i] * pct2);
	    }
	    return new Color(clr1[0], clr1[1], clr1[2], clr1[3]);
	}
	
	private void paintBorderShadow(Graphics2D g2, int shadowWidth, Shape shape) {
	    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
	                        RenderingHints.VALUE_ANTIALIAS_ON);
	    int sw = shadowWidth*2;
	    for (int i=sw; i >= 2; i-=2) {
	        float pct = (float)(sw - i) / (sw - 1);
	        g2.setColor(getMixedColor(Color.LIGHT_GRAY, pct,
	                                  Color.WHITE, 1.0f-pct));
	        g2.setStroke(new BasicStroke(i));
	        g2.draw(shape);
	    }
	}
	
	@Override
	protected void paint(PPaintContext paintContext) {
		Graphics2D g2 = paintContext.getGraphics();
		
		Shape shape = this.getBounds();
		
		g2.setPaint(BACKGROUND_COLOR);

		Rectangle r = shape.getBounds();
		g2.fillRect(r.x, r.y, r.width, r.height);
		
		paintBorderShadow(g2, 2, shape);
		
		g2.setPaint(Color.black);
		g2.setStroke(borderStroke);
		g2.draw(shape);
	}
	
	class GraphTextNode extends PText {
		private static final long serialVersionUID = -871571524212274580L;
		
		private boolean ignoreInvalidatePaint = false;

//		@Override
//		public Font getFont() {
//			Font font = PText.DEFAULT_FONT;
//			if (font == null) {
//				font = DEFAULT_FONT;
//			}
//			return font;
//		}

		@Override
		public Paint getTextPaint() {
			Paint paint = Color.black;
			if (paint == null) {
				paint = Color.black;
			}
			return paint;
		}

		@Override
		protected void paint(PPaintContext paintContext) {
			// update the text paint - the super paint method doesn't call our getTextPaint() method
			Paint p = getTextPaint();
			if (!p.equals(super.getTextPaint())) {
				ignoreInvalidatePaint = true;
				setTextPaint(getTextPaint());
				ignoreInvalidatePaint = false;
			}
			// the font is never set in the super paint class?
			paintContext.getGraphics().setFont(getFont());
			super.paint(paintContext);
		}

		@Override
		public void invalidatePaint() {
			if (!ignoreInvalidatePaint) {
				super.invalidatePaint();
			}
		}
	}
}
