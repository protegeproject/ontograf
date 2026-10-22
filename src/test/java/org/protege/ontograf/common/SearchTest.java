package org.protege.ontograf.common;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.regex.Pattern;
public class SearchTest {
    @Test public void literalModesEscapeMetacharacters() {
        String expression = GraphController.prepareSearchString("A[1].*", GraphController.EXACT_MATCH);
        assertTrue(Pattern.compile(expression).matcher("A[1].*").find());
        assertFalse(Pattern.compile(expression).matcher("A1zzz").find());
    }
    @Test(expected = java.util.regex.PatternSyntaxException.class) public void invalidRegexIsReported() {
        GraphController.prepareSearchString("[", GraphController.REGEXP);
    }
}
