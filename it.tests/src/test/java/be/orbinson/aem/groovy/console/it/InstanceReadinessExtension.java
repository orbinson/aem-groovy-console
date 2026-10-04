package be.orbinson.aem.groovy.console.it;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.awaitility.Awaitility.await;

/** Waits once per run until the Sling installer has processed every content package of the launched feature. */
public class InstanceReadinessExtension implements BeforeAllCallback {

    private static final String BASE_URL = "http://localhost:" + Integer.getInteger("HTTP_PORT", 8080);
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static final List<String> FEATURE_PACKAGES = readFeaturePackages();

    // IGNORED: ui.apps.aem needs AEM node types and can never install on Sling.
    private static final Pattern TERMINAL_STATE = Pattern.compile("<td>(?:INSTALLED|UNINSTALLED|IGNORED)\\b");

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(InstanceReadinessExtension.class, k -> {
                    awaitReady();
                    return Boolean.TRUE;
                });
    }

    private static void awaitReady() {
        await("installer done with all " + FEATURE_PACKAGES.size() + " content packages of the launched feature")
                .atMost(Duration.ofSeconds(240))
                .pollInterval(Duration.ofSeconds(1))
                .until(InstanceReadinessExtension::installerIdle);

        await("console servlet answering")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(500))
                .until(InstanceReadinessExtension::consoleAnswers);
    }

    static boolean installerIdle() {
        String body = get("/system/console/osgi-installer.json");
        if (body == null) {
            return false;
        }
        int active = body.indexOf("Active Resources");
        int processed = body.indexOf("Processed Resources");
        if (active < 0 || processed < active || !body.substring(active, processed).contains("<li>none</li>")) {
            return false;
        }
        int untransformed = body.indexOf("Untransformed Resources - ");
        return FEATURE_PACKAGES.stream().allMatch(file -> {
            Matcher row = Pattern.compile("<tr>(?:(?!</tr>).)*/" + Pattern.quote(file) + "(?:(?!</tr>).)*</tr>", Pattern.DOTALL).matcher(body);
            while (row.find()) {
                if ((untransformed >= 0 && row.start() > untransformed) || TERMINAL_STATE.matcher(row.group()).find()) {
                    return true;
                }
            }
            return false;
        });
    }

    private static List<String> readFeaturePackages() {
        String featureFile = System.getProperty("IT_FEATURE_FILE");
        if (featureFile == null) {
            throw new IllegalStateException("IT_FEATURE_FILE system property not set (configured in it.tests/pom.xml)");
        }
        try {
            JsonObject feature = JsonParser.parseString(new String(Files.readAllBytes(Paths.get(featureFile)),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> files = new ArrayList<>();
            for (Map.Entry<String, JsonElement> extension : feature.entrySet()) {
                if (!extension.getKey().startsWith("content-packages:")) {
                    continue;
                }
                for (JsonElement artifact : extension.getValue().getAsJsonArray()) {
                    String id = artifact.isJsonObject() ? artifact.getAsJsonObject().get("id").getAsString() : artifact.getAsString();
                    // group:artifact:type:classifier:version -> artifact-version-classifier.type
                    String[] p = id.split(":");
                    files.add(p.length == 5 ? p[1] + "-" + p[4] + "-" + p[3] + "." + p[2] : p[1] + "-" + p[p.length - 1] + ".zip");
                }
            }
            if (files.isEmpty()) {
                throw new IllegalStateException("No content packages found in " + featureFile);
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean consoleAnswers() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
            post.setEntity(new StringEntity("script=return 'ready'", ContentType.APPLICATION_FORM_URLENCODED));
            post.addHeader("Authorization", AUTH);
            post.addHeader("Connection", "close");
            try (CloseableHttpResponse response = client.execute(post)) {
                return response.getStatusLine().getStatusCode() == 200
                        && EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).contains("ready");
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static String get(String path) {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", AUTH);
        get.addHeader("Connection", "close");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(get)) {
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return response.getStatusLine().getStatusCode() == 200 ? body : null;
        } catch (IOException e) {
            return null;
        }
    }
}
