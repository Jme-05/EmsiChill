package me.jaime.emsichill.skin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Consulta perfiles y texturas firmadas, aplica caché temporal y evita solicitudes remotas
 * duplicadas para una misma skin.
 */
public final class SkinProvider {
    private static final String MINESKIN_URL = "https://api.mineskin.org/v2/skins";
    private static final String USER_AGENT = "EmsiChill/5.0.0 (Minecraft plugin)";

    private final SkinRepository repository;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final Random random = new Random();

    private SkinSettings settings;
    private int providerFailures;
    private long providerRetryAfter;

    public SkinProvider(final SkinRepository repository, final SkinSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    public void updateSettings(final SkinSettings settings) {
        this.settings = settings;
    }

    public SkinTexture findByName(final String skinName) throws IOException, InterruptedException {
        SkinTexture cached = this.repository.cachedTexture(skinName);
        long now = Instant.now().getEpochSecond();
        // La firma almacenada se reutiliza mientras siga dentro del tiempo de vida configurado.
        if (cached != null && now - cached.fetchedAt() <= this.settings.cacheLifetimeSeconds()) {
            return cached;
        }

        SkinTexture fetched;
        try {
            fetched = this.fetchMojangTexture(skinName, now);
        } catch (RuntimeException exception) {
            throw new IOException("Mojang returned an invalid response", exception);
        }
        if (fetched != null) {
            this.repository.cacheTexture(fetched);
        }
        return fetched;
    }

    public SkinTexture findRandom() throws IOException, InterruptedException {
        IOException providerFailure = null;
        long now = Instant.now().getEpochSecond();

        // Tras fallos consecutivos se pausa el catálogo público para no castigar cada comando.
        if (this.settings.usePublicCatalog() && now >= this.providerRetryAfter) {
            try {
                SkinTexture texture = this.fetchMineSkinTexture();
                this.markProviderAvailable();
                if (texture != null) {
                    this.repository.cacheTexture(texture);
                    return texture;
                }
            } catch (IOException exception) {
                providerFailure = exception;
                this.markProviderUnavailable(now);
            } catch (RuntimeException exception) {
                providerFailure = new IOException("MineSkin returned an invalid response", exception);
                this.markProviderUnavailable(now);
            }
        } else if (now < this.providerRetryAfter) {
            providerFailure = new IOException("MineSkin is temporarily paused after a previous failure");
        }

        // Las skins configuradas y ya conocidas mantienen /skin random útil si el catálogo falla.
        List<String> fallbackNames = new ArrayList<>(new LinkedHashSet<>(this.settings.fallbackPremiumNames()));
        fallbackNames.addAll(this.repository.cachedSkinNames());
        Collections.shuffle(fallbackNames, this.random);
        for (String name : fallbackNames) {
            if (!SkinSettings.isValidMinecraftName(name)) {
                continue;
            }
            SkinTexture texture = this.findByName(name);
            if (texture != null) {
                return texture;
            }
        }

        if (providerFailure != null) {
            throw providerFailure;
        }
        return null;
    }

    private SkinTexture fetchMojangTexture(final String skinName, final long fetchedAt)
        throws IOException, InterruptedException {
        HttpResponse<String> profileResponse = this.sendJson(
            "https://api.mojang.com/users/profiles/minecraft/" + skinName
        );
        if (profileResponse.statusCode() != 200) {
            return null;
        }

        JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
        String uuid = string(profile, "id");
        String resolvedName = string(profile, "name");
        if (uuid == null || resolvedName == null) {
            return null;
        }

        HttpResponse<String> textureResponse = this.sendJson(
            "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"
        );
        if (textureResponse.statusCode() != 200) {
            return null;
        }

        JsonArray properties = JsonParser.parseString(textureResponse.body())
            .getAsJsonObject()
            .getAsJsonArray("properties");
        if (properties == null) {
            return null;
        }
        for (JsonElement element : properties) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(string(property, "name"))) {
                continue;
            }
            String value = string(property, "value");
            String signature = string(property, "signature");
            if (value != null && signature != null) {
                return new SkinTexture(resolvedName, value, signature, fetchedAt);
            }
        }
        return null;
    }

    private SkinTexture fetchMineSkinTexture() throws IOException, InterruptedException {
        HttpResponse<String> listResponse = this.sendJson(MINESKIN_URL);
        if (listResponse.statusCode() != 200) {
            throw new IOException("MineSkin list returned HTTP " + listResponse.statusCode());
        }

        JsonArray skins = JsonParser.parseString(listResponse.body()).getAsJsonObject().getAsJsonArray("skins");
        if (skins == null || skins.isEmpty()) {
            return null;
        }

        List<String> skinIds = new ArrayList<>();
        for (JsonElement element : skins) {
            if (element.isJsonObject()) {
                String uuid = string(element.getAsJsonObject(), "uuid");
                if (uuid != null) {
                    skinIds.add(uuid);
                }
            }
        }
        Collections.shuffle(skinIds, this.random);

        int attempts = Math.min(this.settings.randomAttempts(), skinIds.size());
        for (int index = 0; index < attempts; index++) {
            HttpResponse<String> detailResponse = this.sendJson(MINESKIN_URL + "/" + skinIds.get(index));
            if (detailResponse.statusCode() == 200) {
                SkinTexture texture = this.parseMineSkinTexture(detailResponse.body());
                if (texture != null) {
                    return texture;
                }
            }
        }
        return null;
    }

    private SkinTexture parseMineSkinTexture(final String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject skin = root.getAsJsonObject("skin");
        JsonObject texture = skin == null ? null : skin.getAsJsonObject("texture");
        JsonObject data = texture == null ? null : texture.getAsJsonObject("data");
        if (data == null) {
            return null;
        }

        String value = string(data, "value");
        String signature = string(data, "signature");
        if (value == null || signature == null || signature.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            String premiumName = string(JsonParser.parseString(decoded).getAsJsonObject(), "profileName");
            if (!SkinSettings.isValidMinecraftName(premiumName)) {
                return null;
            }
            return new SkinTexture(premiumName, value, signature, Instant.now().getEpochSecond());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
    }

    private HttpResponse<String> sendJson(final String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(this.settings.requestTimeout())
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private synchronized void markProviderAvailable() {
        this.providerFailures = 0;
        this.providerRetryAfter = 0L;
    }

    private synchronized void markProviderUnavailable(final long now) {
        this.providerFailures = Math.min(10, this.providerFailures + 1);
        long multiplier = 1L << Math.min(6, this.providerFailures - 1);
        long retrySeconds = Math.min(
            this.settings.retryMaximumSeconds(),
            this.settings.retryBaseSeconds() * multiplier
        );
        this.providerRetryAfter = now + retrySeconds;
    }

    private static String string(final JsonObject object, final String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }
}