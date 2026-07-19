package me.jaime.emsichill.update;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Consulta el feed publico de Releases cuando la API de GitHub no esta disponible. */
final class GitHubAtomReleaseClient {
    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9A-Fa-f]+);");

    private final HttpClient client;
    private final Duration timeout;
    private final String repository;
    private final String userAgent;

    GitHubAtomReleaseClient(
        final HttpClient client,
        final String repository,
        final String userAgent,
        final Duration timeout
    ) {
        this.client = client;
        this.repository = repository;
        this.userAgent = userAgent;
        this.timeout = timeout;
    }

    CompletableFuture<ReleaseInfo> latestRelease() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://github.com/" + this.repository + "/releases.atom"))
            .timeout(this.timeout)
            .header("Accept", "application/atom+xml")
            .header("User-Agent", this.userAgent)
            .GET()
            .build();
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("El feed de GitHub respondio con HTTP " + response.statusCode());
                }
                return parse(response.body(), this.repository);
            });
    }

    static ReleaseInfo parse(final String xml, final String repository) {
        try {
            Element feed = secureFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getDocumentElement();
            NodeList entries = feed.getElementsByTagNameNS(ATOM_NAMESPACE, "entry");
            if (entries.getLength() == 0) throw new IllegalStateException("El feed de GitHub no contiene Releases");

            Element entry = (Element) entries.item(0);
            String pageUrl = alternateLink(entry);
            String tag = tagFromPage(pageUrl);
            String notes = htmlToText(childText(entry, "content"));
            String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            String assetName = "EmsiChill-" + version + ".jar";
            String downloadUrl = "https://github.com/" + repository + "/releases/download/" + tag + "/" + assetName;
            return new ReleaseInfo(tag, pageUrl, notes, new ReleaseAsset(assetName, downloadUrl, -1L, null));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo leer el feed de Releases", exception);
        }
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory;
    }

    private static String alternateLink(final Element entry) {
        NodeList links = entry.getElementsByTagNameNS(ATOM_NAMESPACE, "link");
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            if (link.getAttribute("rel").equals("alternate") && !link.getAttribute("href").isBlank()) {
                return link.getAttribute("href");
            }
        }
        throw new IllegalStateException("La Release del feed no contiene enlace");
    }

    private static String childText(final Element parent, final String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && element.getLocalName().equals(name)) {
                return element.getTextContent();
            }
        }
        return "";
    }

    private static String tagFromPage(final String pageUrl) {
        String marker = "/releases/tag/";
        int position = pageUrl.indexOf(marker);
        if (position < 0) throw new IllegalStateException("Enlace de Release no reconocido");
        String tag = pageUrl.substring(position + marker.length());
        if (tag.isBlank() || tag.contains("/") || tag.contains("?")) {
            throw new IllegalStateException("Etiqueta de Release no reconocida");
        }
        return tag;
    }

    private static String htmlToText(final String html) {
        String text = html
            .replaceAll("(?i)<li[^>]*>", "- ")
            .replaceAll("(?i)</(p|li|h[1-6]|ol|ul)>", "\n")
            .replaceAll("<[^>]+>", "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ");
        Matcher matcher = NUMERIC_ENTITY.matcher(text);
        StringBuffer decoded = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            int radix = value.startsWith("x") ? 16 : 10;
            String digits = radix == 16 ? value.substring(1) : value;
            try {
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(
                    Character.toString(Integer.parseInt(digits, radix))));
            } catch (IllegalArgumentException exception) {
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(decoded);
        return decoded.toString().replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
    }
}
