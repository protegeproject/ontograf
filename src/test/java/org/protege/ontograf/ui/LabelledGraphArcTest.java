package org.protege.ontograf.ui;

import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.geom.*;
import ca.uvic.cs.chisel.cajun.graph.node.DefaultGraphNode;

public class LabelledGraphArcTest {
    @Test public void measuresPathLengthAndClosedLoops() {
        Point2D straight = LabelledGraphArc.midpoint(new Line2D.Double(0, 0, 100, 0));
        assertEquals(50, straight.getX(), .01);
        Point2D curve = LabelledGraphArc.midpoint(new QuadCurve2D.Double(0, 0, 50, 100, 100, 0));
        assertEquals(50, curve.getX(), .5); assertEquals(50, curve.getY(), .5);
        Point2D loop = LabelledGraphArc.midpoint(new Ellipse2D.Double(0, 0, 40, 40));
        assertTrue(Double.isFinite(loop.getX())); assertTrue(Double.isFinite(loop.getY()));
    }
    @Test public void modesAndGeometryFollowSelectionAndDragging() {
        DefaultGraphNode source = new DefaultGraphNode("source"), target = new DefaultGraphNode("target");
        source.setBounds(0, 0, 40, 20); target.setBounds(200, 0, 40, 20);
        EdgeLabelMode[] mode = { EdgeLabelMode.SELECTED };
        LabelledGraphArc arc = new LabelledGraphArc("edge", source, target, null, "works for", () -> mode[0]);
        arc.updateArcPath();
        assertFalse(arc.isLabelShown(1)); source.setSelected(true); assertTrue(arc.isLabelShown(1));
        assertFalse(arc.isLabelShown(.2)); mode[0] = EdgeLabelMode.OFF; assertFalse(arc.isLabelShown(1));
        mode[0] = EdgeLabelMode.ALL; assertTrue(arc.isLabelShown(1));
        double oldX = arc.getChild(0).getXOffset(); target.setBounds(400, 0, 40, 20); arc.updateArcPath();
        assertTrue(arc.getChild(0).getXOffset() > oldX);
        assertFalse(arc.getChild(0).getPickable());
    }
    @Test public void exportedPixelsIncludeLabelsOnlyWhenEnabled() {
        DefaultGraphNode source = new DefaultGraphNode("source"), target = new DefaultGraphNode("target");
        source.setBounds(0, 0, 40, 20); target.setBounds(200, 0, 40, 20);
        EdgeLabelMode[] mode = { EdgeLabelMode.ALL };
        LabelledGraphArc arc = new LabelledGraphArc("edge", source, target, null, "works for", () -> mode[0]);
        arc.updateArcPath();
        java.awt.image.BufferedImage on = render(arc);
        mode[0] = EdgeLabelMode.OFF;
        java.awt.image.BufferedImage off = render(arc);
        int changed = 0;
        for (int x = 0; x < on.getWidth(); x++) for (int y = 0; y < on.getHeight(); y++) if (on.getRGB(x, y) != off.getRGB(x, y)) changed++;
        assertTrue("Image export must include label pixels", changed > 30);
    }
    @Test public void allModeKeepsLabelPixelsWhenCanvasClickClearsSelection() {
        DefaultGraphNode source = new DefaultGraphNode("source"), target = new DefaultGraphNode("target");
        source.setBounds(0, 0, 40, 20); target.setBounds(200, 0, 40, 20);
        EdgeLabelMode[] mode = { EdgeLabelMode.ALL };
        LabelledGraphArc arc = new LabelledGraphArc("edge", source, target, null, "works for", () -> mode[0]);
        source.addArc(arc); target.addArc(arc);
        arc.updateArcPath();
        source.setSelected(true);
        java.awt.image.BufferedImage selected = render(arc);
        source.setSelected(false); target.setSelected(false);
        assertFalse(arc.isSelected());
        assertTrue(arc.isLabelShown(1));
        java.awt.image.BufferedImage deselected = render(arc);
        // Compare the label region above the edge, excluding the selection-dependent arrowhead.
        for (int x = 50; x < 300; x++) for (int y = 35; y < 77; y++)
            assertEquals("All-mode label pixels must not depend on selection", selected.getRGB(x, y), deselected.getRGB(x, y));
        mode[0] = EdgeLabelMode.SELECTED;
        assertFalse(arc.isLabelShown(1));
        target.setSelected(true);
        assertTrue(arc.isLabelShown(1));
        target.setSelected(false);
        assertFalse(arc.isLabelShown(1));
    }
    private java.awt.image.BufferedImage render(LabelledGraphArc arc) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(400, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try { graphics.translate(50, 80); arc.fullPaint(new edu.umd.cs.piccolo.util.PPaintContext(graphics)); }
        finally { graphics.dispose(); }
        return image;
    }
}
