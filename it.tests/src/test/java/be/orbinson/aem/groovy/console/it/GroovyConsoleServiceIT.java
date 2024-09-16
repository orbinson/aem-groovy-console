package be.orbinson.aem.groovy.console.it;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GroovyConsoleServiceIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);

    @Test
    void testScriptReturnsResult() throws Exception {
        String script = "print 'test'";
        Map response = executeScript(script);
        assertNotNull(response, "Could not get response from API");
        assertEquals("test", response.get("output"));
    }

    private static Map executeScript(String script) throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost executeScript = new HttpPost("http://localhost:" + SLING_PORT + "/bin/groovyconsole/post");
            List<BasicNameValuePair> basicNameValuePairs = new java.util.ArrayList<>();
            basicNameValuePairs.add(new BasicNameValuePair("script", script));
            executeScript.setEntity(new UrlEncodedFormEntity(basicNameValuePairs, StandardCharsets.UTF_8));
            executeScript.addHeader("Authorization", "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8)));
            try (CloseableHttpResponse response = httpclient.execute(executeScript)) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                try {
                    return new Gson().fromJson(body, Map.class);
                } catch (JsonSyntaxException e) {
                    System.out.println("Could not parse body from JSON: " + e.getMessage());
                    System.out.println(body);
                    return null;
                }
            }
        }
    }
}
