package org.protege.ontograf.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.Icon;

import ca.uvic.cs.chisel.cajun.graph.arc.DefaultGraphArc;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolo.util.PPaintContext;

/** Text is an arc child, so filtering, removal and image export share its lifecycle. */
public class LabelledGraphArc extends DefaultGraphArc {
    private static final long serialVersionUID = 1L;
    private final PText label;
    private String labelText;
    public String getLabelText() { return labelText; }
    private final Supplier<EdgeLabelMode> mode;

    public LabelledGraphArc(Object key, GraphNode source, GraphNode target, Icon icon,
                            Object type, Supplier<EdgeLabelMode> mode) {
        super(key, source, target, icon, type);
        this.mode = mode;
        label = new PText() {
            private static final long serialVersionUID = 1L;
            @Override protected void paint(PPaintContext context) {
                if (isLabelShown(context.getScale())) super.paint(context);
            }
        };
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        label.setTextPaint(Color.BLACK);
        label.setPaint(new Color(255, 255, 255, 235));
        label.setPickable(false);
        label.setChildrenPickable(false);
        addChild(label);
        updateLabel();
    }

    public boolean isLabelShown(double scale) {
        EdgeLabelMode current = mode.get();
        return scale >= 0.45 && current != EdgeLabelMode.OFF
                && (current == EdgeLabelMode.ALL || isSelected()
                    || getSource().isSelected() || getDestination().isSelected());
    }

    public void updateLabel() {
        setLabelText(getType() instanceof org.protege.ontograf.model.Relationship.Type
                ? ((org.protege.ontograf.model.Relationship.Type)getType()).getLabel() : String.valueOf(getType()));
    }

    public void setLabelText(String value) {
        labelText = value;
        String text = value.replaceAll("[\\r\\n\\t]+", " ");
        // Truncate by code point to avoid splitting supplementary Unicode characters.
        int count = text.codePointCount(0, text.length());
        if (count > 48) text = text.substring(0, text.offsetByCodePoints(0, 47)) + "…";
        label.setText(" " + text + " ");
        positionLabel();
    }

    @Override public void updateArcPath() {
        super.updateArcPath();
        if (label != null) positionLabel();
    }

    private void positionLabel() {
        Point2D point = midpoint(getPathReference());
        label.setOffset(point.getX() - label.getWidth() / 2,
                        point.getY() - label.getHeight() - 12);
    }

    /** Flatten curves and locate half the total path length, including closed loops. */
    public static Point2D midpoint(Shape shape) {
        List<double[]> segments = new ArrayList<>();
        PathIterator path = shape.getPathIterator(null, 0.5);
        double[] coords = new double[6];
        double x = 0, y = 0, startX = 0, startY = 0, total = 0;
        while (!path.isDone()) {
            int kind = path.currentSegment(coords);
            if (kind == PathIterator.SEG_MOVETO) {
                x = startX = coords[0]; y = startY = coords[1];
            } else {
                double nextX = kind == PathIterator.SEG_CLOSE ? startX : coords[0];
                double nextY = kind == PathIterator.SEG_CLOSE ? startY : coords[1];
                double length = Point2D.distance(x, y, nextX, nextY);
                if (length > 0) segments.add(new double[] {x, y, nextX, nextY, length});
                total += length; x = nextX; y = nextY;
            }
            path.next();
        }
        double remaining = total / 2;
        for (double[] segment : segments) {
            if (remaining <= segment[4]) {
                double fraction = remaining / segment[4];
                return new Point2D.Double(segment[0] + fraction * (segment[2] - segment[0]),
                        segment[1] + fraction * (segment[3] - segment[1]));
            }
            remaining -= segment[4];
        }
        return new Point2D.Double(x, y);
    }
}
