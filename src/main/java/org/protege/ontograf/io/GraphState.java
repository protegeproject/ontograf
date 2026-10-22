package org.protege.ontograf.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned UTF-8 view document. Parsing never mutates the displayed graph. */
public final class GraphState {
    public String ontology;
    public String labelMode = "SELECTED";
    public String annotation = "", language = "en", layout = "";
    public double[] camera = {1, 0, 0, 1, 0, 0};
    public final List<Node> nodes = new ArrayList<>();
    public final Map<String, Boolean> filters = new LinkedHashMap<>();
    public final Map<String, Boolean> nodeFilters = new LinkedHashMap<>();
    public boolean legacy;
    public static final class Node {
        public final String type, iri;
        public final double x, y;
        public final boolean pinned;
        public Node(String type, String iri, double x, double y, boolean pinned) {
            this.type = type; this.iri = iri; this.x = x; this.y = y; this.pinned = pinned;
        }
    }
    public static GraphState read(Path file) throws IOException {
        if (Files.size(file) > 8 * 1024 * 1024) throw new IOException("Graph file exceeds 8 MB");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IOException("Empty graph file");
        GraphState state = new GraphState();
        try {
            if (!lines.get(0).equals("# OntoGraf view")) {
                state.legacy = true; state.ontology = lines.get(0);
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i); if (line.trim().isEmpty()) continue;
                    int last = line.lastIndexOf(','), prior = line.lastIndexOf(',', last - 1);
                    if (prior < 1) throw new IllegalArgumentException("Invalid legacy node row " + (i + 1));
                    state.nodes.add(new Node("", line.substring(0, prior), number(line.substring(prior + 1, last)), number(line.substring(last + 1)), false));
                }
            } else {
                Properties values = new Properties();
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
                if (!"2".equals(values.getProperty("version"))) throw new IllegalArgumentException("Unsupported graph file version");
                state.ontology = required(values, "ontology");
                state.labelMode = required(values, "labels");
                org.protege.ontograf.ui.EdgeLabelMode.valueOf(state.labelMode);
                state.annotation = values.getProperty("annotation", ""); state.language = values.getProperty("language", "en");
                state.layout = values.getProperty("layout", "");
                for (int i = 0; i < 6; i++) state.camera[i] = number(required(values, "camera." + i));
                if (Math.abs(state.camera[0] * state.camera[3] - state.camera[1] * state.camera[2]) < 1e-12) throw new IllegalArgumentException("Invalid camera transform");
                int count = Integer.parseInt(required(values, "nodes"));
                if (count < 0 || count > 100000) throw new IllegalArgumentException("Invalid node count");
                Set<String> identities = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    String prefix = "node." + i + ".";
                    String type = required(values, prefix + "type"), iri = required(values, prefix + "iri");
                    if (!type.equals("Class") && !type.equals("NamedIndividual")) throw new IllegalArgumentException("Unsupported node type: " + type);
                    if (!identities.add(type + "\n" + iri)) throw new IllegalArgumentException("Duplicate node");
                    state.nodes.add(new Node(type, iri, number(required(values, prefix + "x")), number(required(values, prefix + "y")), bool(required(values, prefix + "pinned"))));
                }
                for (String key : values.stringPropertyNames()) {
                    if (key.startsWith("filter.")) state.filters.put(key.substring(7), bool(values.getProperty(key)));
                    if (key.startsWith("nodeFilter.")) state.nodeFilters.put(key.substring(11), bool(values.getProperty(key)));
                }
            }
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid graph file: " + ex.getMessage(), ex); }
        return state;
    }
    private static String required(Properties values, String key) {
        String value = values.getProperty(key); if (value == null) throw new IllegalArgumentException("Missing " + key); return value;
    }
    private static boolean bool(String text) {
        if (!"true".equals(text) && !"false".equals(text)) throw new IllegalArgumentException("Invalid boolean");
        return Boolean.parseBoolean(text);
    }
    private static double number(String text) {
        double value = Double.parseDouble(text); if (!Double.isFinite(value) || Math.abs(value) > 1e9) throw new IllegalArgumentException("Invalid coordinate"); return value;
    }
    public void write(Path file) throws IOException {
        Properties values = new Properties();
        values.setProperty("version", "2"); values.setProperty("ontology", ontology); values.setProperty("labels", labelMode);
        values.setProperty("annotation", annotation); values.setProperty("language", language); values.setProperty("layout", layout);
        for (int i = 0; i < 6; i++) values.setProperty("camera." + i, Double.toString(camera[i]));
        values.setProperty("nodes", Integer.toString(nodes.size()));
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i); String prefix = "node." + i + ".";
            values.setProperty(prefix + "type", node.type); values.setProperty(prefix + "iri", node.iri);
            values.setProperty(prefix + "x", Double.toString(node.x)); values.setProperty(prefix + "y", Double.toString(node.y));
            values.setProperty(prefix + "pinned", Boolean.toString(node.pinned));
        }
        filters.forEach((key, value) -> values.setProperty("filter." + key, value.toString()));
        nodeFilters.forEach((key, value) -> values.setProperty("nodeFilter." + key, value.toString()));
        Path absolute = file.toAbsolutePath(), temporary = Files.createTempFile(absolute.getParent(), ".ontograf-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write("# OntoGraf view\n"); values.store(writer, "View state version 2");
            }
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
