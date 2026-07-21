package be.orbinson.aem.groovy.console.samples;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jcr.Node;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@code FulltextQuery.groovy} sample: builds a QueryBuilder query and prints the hits. Shows how to supply
 * a configured AEM OSGi service: the {@link QueryBuilder} is registered via {@code beforeSetUp(...)} before
 * {@link ContextPlugins#GROOVY_CONSOLE}.
 */
@ExtendWith(AemContextExtension.class)
class FulltextQueryTest {

    private final Node node = mock(Node.class);
    private final Hit hit = mock(Hit.class);
    private final SearchResult searchResult = mock(SearchResult.class);
    private final Query query = mock(Query.class);
    private final QueryBuilder queryBuilder = mock(QueryBuilder.class);

    private final AemContext context = new AemContextBuilder()
            .beforeSetUp(osgiContext -> osgiContext.registerService(QueryBuilder.class, queryBuilder))
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @BeforeEach
    void stubQuery() throws Exception {
        when(node.getPath()).thenReturn("/content/we-retail/en");
        when(hit.getNode()).thenReturn(node);
        when(searchResult.getTotalMatches()).thenReturn(1L);
        when(searchResult.getExecutionTime()).thenReturn("0.1");
        when(searchResult.getHits()).thenReturn(List.of(hit));
        when(query.getResult()).thenReturn(searchResult);
        when(queryBuilder.createQuery(any(), any())).thenReturn(query);
    }

    @Test
    void printsQueryHits() {
        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("FulltextQuery.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertTrue(response.getOutput().contains("1 hits"), response.getOutput());
        assertTrue(response.getOutput().contains("hit path = /content/we-retail/en"), response.getOutput());
    }
}
