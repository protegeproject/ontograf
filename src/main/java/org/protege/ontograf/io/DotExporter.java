package org.protege.ontograf.io;
import java.io.*;
import java.util.*;
import ca.uvic.cs.chisel.cajun.graph.GraphModel;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;
import ca.uvic.cs.chisel.cajun.graph.arc.GraphArc;
import org.protege.ontograf.model.Relationship;
import org.semanticweb.owlapi.model.OWLEntity;

public final class DotExporter {
    private DotExporter() { }
    public static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
    private static String identity(GraphNode node) {
        if (node.getUserObject() instanceof OWLEntity) {
            OWLEntity entity = (OWLEntity)node.getUserObject();
            return entity.getEntityType().getName() + ":" + entity.getIRI();
        }
        return node.getUserObject().toString();
    }
    public static void write(GraphModel model, Writer writer) throws IOException {
        writer.write("digraph ontograf {\n");
        for (GraphNode node : model.getAllNodes()) if (node.isVisible())
            writer.write("  " + quote(identity(node)) + " [label=" + quote(node.getText()) + "];\n");
        for (GraphArc arc : model.getAllArcs()) if (arc.isVisible() && arc.getSource().isVisible() && arc.getDestination().isVisible()) {
            GraphNode source = arc.isInverted() ? arc.getDestination() : arc.getSource();
            GraphNode target = arc.isInverted() ? arc.getSource() : arc.getDestination();
            String label = arc instanceof org.protege.ontograf.ui.LabelledGraphArc
                    ? ((org.protege.ontograf.ui.LabelledGraphArc)arc).getLabelText() : arc.getType() instanceof Relationship.Type ? ((Relationship.Type)arc.getType()).getLabel() : arc.getType().toString();
            writer.write("  " + quote(identity(source)) + " -> " + quote(identity(target)) + " [label=" + quote(label) + "];\n");
        }
        writer.write("}\n");
    }
}
