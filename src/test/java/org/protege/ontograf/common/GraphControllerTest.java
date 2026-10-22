package org.protege.ontograf.common;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.find.OWLEntityFinder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

public class GraphControllerTest {
    @Test public void restoresViewsAndUnregistersExternalListeners() throws Exception {
        OWLOntologyManager ontologies = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = ontologies.loadOntologyFromOntologyDocument(getClass().getResourceAsStream("/relationships.ofn"));
        OWLModelManager manager = mock(OWLModelManager.class);
        when(manager.getActiveOntology()).thenReturn(ontology);
        when(manager.getActiveOntologies()).thenReturn(Collections.singleton(ontology));
        when(manager.getOWLDataFactory()).thenReturn(ontologies.getOWLDataFactory());
        when(manager.getRendering(any(OWLObject.class))).thenAnswer(invocation -> invocation.getArgument(0).toString());
        OWLEntityFinder finder = mock(OWLEntityFinder.class);
        when(manager.getOWLEntityFinder()).thenReturn(finder);
        when(finder.getEntities(any(IRI.class))).thenAnswer(invocation -> ontology.getEntitiesInSignature(invocation.getArgument(0)));
        OWLEditorKit kit = mock(OWLEditorKit.class);
        when(kit.getModelManager()).thenReturn(manager); when(kit.getOWLModelManager()).thenReturn(manager);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            GraphController controller = null;
            try {
                controller = new GraphController(new JPanel(), kit);
                OWLClass person = ontologies.getOWLDataFactory().getOWLClass(IRI.create("urn:ontograf:test#Person"));
                controller.getModel().show(person, ((ca.uvic.cs.chisel.cajun.graph.FlatGraph)controller.getGraph()).getFilterManager());
                controller.getModel().expandNode(controller.getModel().getNode(person));
                controller.getModel().setPinned(person, true);
                controller.getModel().getNode(person).setLocation(100, 200);
                org.protege.ontograf.io.GraphState saved = controller.captureState();
                int count = controller.getModel().getAllNodes().size();
                controller.clear();
                assertEquals(0, controller.restoreState(saved));
                assertEquals(count, controller.getModel().getAllNodes().size());
                assertTrue(controller.getModel().getPinnedEntities().contains(person));
                assertEquals(100, controller.getModel().getNode(person).getBounds().getX(), .01);
                saved.ontology = "wrong ontology";
                try { controller.restoreState(saved); fail("Mismatched ontology accepted"); } catch (java.io.IOException expected) { }
                assertEquals(count, controller.getModel().getAllNodes().size());
            } catch (Throwable ex) { failure.set(ex); }
            finally { if (controller != null) controller.dispose(); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        verify(manager).removeOntologyChangeListener(any()); verify(manager).removeListener(any());
    }
}
