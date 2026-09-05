package be.orbinson.aem.groovy.console.it;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Scanner;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;

/**
 * Test helper for provisioning repository content over HTTP via the SlingPostServlet, so integration tests can
 * reference real script files from {@code src/test/resources} instead of building scripts from concatenated strings.
 */
final class SlingContent {

    private SlingContent() {
    }

    /**
     * Deploy a classpath resource as an {@code nt:file} at {@code jcrPath} using a SlingPostServlet file upload
     * (multipart POST to the parent, part named after the file — exactly what {@code curl -F name=@file parent/} does).
     *
     * @param jcrPath absolute path of the resulting nt:file, e.g. {@code /var/groovyconsole/it/AuditTarget.groovy}
     * @param classpathResource resource on the test classpath, e.g. {@code /scripts/audit-target.groovy}
     */
    static void deployFile(CloseableHttpClient client, String baseUrl, String authHeader,
                           String jcrPath, String classpathResource) throws IOException {
        byte[] content = read(classpathResource).getBytes(StandardCharsets.UTF_8);
        int slash = jcrPath.lastIndexOf('/');
        String parent = jcrPath.substring(0, slash);
        String name = jcrPath.substring(slash + 1);

        // POST to the parent WITHOUT a trailing slash: a trailing slash triggers SlingPostServlet's auto node-name
        // generation, which would nest the file under a generated node instead of creating it at `parent/name`.
        HttpPost post = new HttpPost(baseUrl + parent);
        post.addHeader("Authorization", authHeader);
        post.addHeader("Connection", "close");
        post.setEntity(MultipartEntityBuilder.create()
                .addBinaryBody(name, content, ContentType.create("application/octet-stream"), name)
                .build());

        execute(client, post, "deploy " + classpathResource + " to " + jcrPath);
    }

    /** Remove a node via the SlingPostServlet {@code :operation=delete}. No-op if the node does not exist. */
    static void delete(CloseableHttpClient client, String baseUrl, String authHeader, String jcrPath) throws IOException {
        HttpPost post = new HttpPost(baseUrl + jcrPath);
        post.addHeader("Authorization", authHeader);
        post.addHeader("Connection", "close");
        post.setEntity(new UrlEncodedFormEntity(
                Collections.singletonList(new BasicNameValuePair(":operation", "delete")), StandardCharsets.UTF_8));
        execute(client, post, "delete " + jcrPath);
    }

    /** Read a text resource from the test classpath. */
    static String read(String classpathResource) {
        try (InputStream stream = SlingContent.class.getResourceAsStream(classpathResource)) {
            if (stream == null) {
                throw new IOException("test resource not found on classpath: " + classpathResource);
            }
            try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
                return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void execute(CloseableHttpClient client, HttpPost post, String description) throws IOException {
        try (CloseableHttpResponse response = client.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            // SlingPostServlet returns 200 (modified) or 201 (created).
            if (status != 200 && status != 201) {
                throw new IOException("failed to " + description + " : HTTP " + status);
            }
        }
    }
}
