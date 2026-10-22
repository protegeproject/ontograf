package org.protege.ontograf.model;
import org.junit.Test;
import static org.junit.Assert.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Repeatable microbenchmark, not an interactive latency or full-plugin benchmark. */
public class RelationshipBenchmarkTest {
    @Test public void indexedLookupMatchesScan() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory(); OWLOntology ontology = manager.createOntology();
        for (int i = 0; i < 5000; i++) manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(
                factory.getOWLClass(IRI.create("urn:node:" + i)), factory.getOWLClass(IRI.create("urn:node:" + (i + 1)))));
        long start = System.nanoTime(); RelationshipIndex index = new RelationshipIndex(Collections.singleton(ontology), factory);
        long build = System.nanoTime() - start;
        OWLClass focus = factory.getOWLClass(IRI.create("urn:node:2500"));
        int repetitions = 1000; long scanCount = 0, indexCount = 0;
        start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) for (Relationship edge : index.all()) if (edge.source.equals(focus) || edge.target.equals(focus)) scanCount++;
        long scan = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) indexCount += index.incident(focus).size();
        long indexed = System.nanoTime() - start;
        assertEquals(scanCount, indexCount); assertEquals(2000, indexCount);
        String result = "Java=" + System.getProperty("java.version") + "\nOS=" + System.getProperty("os.name")
                + "\nProcessors=" + Runtime.getRuntime().availableProcessors() + "\nMaxHeap=" + Runtime.getRuntime().maxMemory()
                + "\nEdges=5000\nLookups=1000\nIndexBuildMs=" + build / 1e6 + "\nScanMs=" + scan / 1e6 + "\nIndexedMs=" + indexed / 1e6 + "\n";
        Files.write(Paths.get("target/relationship-benchmark.txt"), result.getBytes(StandardCharsets.UTF_8));
    }
}
