package dev.alastorkaneki.morphe.extension.reddit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Metadata resolver and original-only media stream helper, shared by both save modes. */
public final class RedditGalleryResolver {
    private static final String ARCHIVE = "https://arctic-shift.photon-reddit.com/api/posts/ids?ids=";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final Pattern CDN_FILE =
            Pattern.compile("^[a-zA-Z0-9_-]{6,70}\\.(?:jpg|jpeg|png|gif|webp)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_ID = Pattern.compile("^[a-zA-Z0-9_-]{6,70}$");
    private static final int MAX_METADATA_BYTES = 8 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 40 * 1024 * 1024;

    public static final class Photo {
        public final String name;
        public final String mime;
        public final String originalUrl;

        Photo(String name, String mime, String originalUrl) {
            this.name = name;
            this.mime = mime;
            this.originalUrl = originalUrl;
        }
    }

    public static final class Gallery {
        public final String id;
        public final String source;
        public final List<Photo> photos;
        Gallery(String id, String source, List<Photo> photos) {
            this.id = id;
            this.source = source;
            this.photos = Collections.unmodifiableList(photos);
        }
    }

    private RedditGalleryResolver() { }

    public static Gallery resolve(String postId) throws IOException {
        if (!postId.matches("[a-z0-9]{5,12}"))
            throw new IOException("Invalid Reddit post ID");
        IOException archiveFailure;
        try {
            JSONObject json = fetchObject(ARCHIVE + postId);
            JSONArray posts = json.optJSONArray("data");
            if (posts == null || posts.length() == 0)
                throw new IOException("Arctic Shift has no archived record for this post");
            JSONObject post = posts.optJSONObject(0);
            if (post == null || !postId.equalsIgnoreCase(post.optString("id")))
                throw new IOException("Arctic Shift returned an unexpected post");
            return parseGallery(post, postId, "Arctic Shift");
        } catch (IOException e) {
            archiveFailure = e;
        }

        // Anonymous Reddit JSON is often HTTP 403, but remains a useful fallback
        // for posts which Arctic Shift has not yet indexed.
        try {
            URL url = new URL("https://www.reddit.com/comments/" + postId + ".json?raw_json=1&limit=0");
            HttpURLConnection connection = open(url, "application/json");
            try {
                JSONArray payload = new JSONArray(readText(connection, MAX_METADATA_BYTES));
                JSONObject post = payload.getJSONObject(0).getJSONObject("data")
                        .getJSONArray("children").getJSONObject(0).getJSONObject("data");
                return parseGallery(post, postId, "Reddit");
            } finally {
                connection.disconnect();
            }
        } catch (IOException | JSONException | IndexOutOfBoundsException e) {
            throw new IOException("Arctic Shift: " + archiveFailure.getMessage()
                    + "; Reddit: " + e.getMessage(), e);
        }
    }

    /** Parses archival and live Reddit gallery metadata. Never selects preview pixels. */
    static Gallery parseGallery(JSONObject post, String expectedId, String source) throws IOException {
        JSONArray items = post.optJSONObject("gallery_data") == null
                ? null : post.optJSONObject("gallery_data").optJSONArray("items");
        JSONObject metadata = post.optJSONObject("media_metadata");
        if (items == null || metadata == null || items.length() == 0)
            throw new IOException("The post has no accessible gallery metadata");
        if (items.length() > 40) throw new IOException("Gallery exceeds safety limit (40 images)");
        List<Photo> photos = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String mediaId = item.optString("media_id");
            if (!MEDIA_ID.matcher(mediaId).matches()) continue;
            JSONObject media = metadata.optJSONObject(mediaId);
            if (media == null || "failed".equalsIgnoreCase(media.optString("status"))) continue;
            String mime = canonicalMime(media.optString("m"));
            String extension = extension(mime);
            if (extension == null) continue;
            JSONObject sourceMedia = media.optJSONObject("s");
            String basename = originalName(sourceMedia == null ? null : sourceMedia.optString("u"));
            if (basename == null) basename = mediaId + "." + extension;
            String url = "https://i.redd.it/" + basename;
            String name = String.format(Locale.US, "%02d_%s.%s", i + 1, mediaId, extension);
            photos.add(new Photo(name, mime, url));
        }
        if (photos.isEmpty())
            throw new IOException("No supported original images (JPEG, PNG, GIF, WebP) in gallery");
        return new Gallery(expectedId, source, photos);
    }

    /** Reddit's archived MIME values use image/jpg for JPEG originals. */
    public static String canonicalMime(String value) {
        if (value == null) return "";
        String mime = value.trim().toLowerCase(Locale.US);
        return "image/jpg".equals(mime) || "image/pjpeg".equals(mime) ? "image/jpeg" : mime;
    }

    private static String extension(String mime) {
        switch (mime) {
            case "image/jpeg": return "jpg";
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "image/webp": return "webp";
            default: return null;
        }
    }

    /**
     * Use only the basename from the archived source URL; never fetch user-supplied,
     * preview.redd.it-resized or off-site image URLs.
     */
    private static String originalName(String archivedUrl) {
        if (archivedUrl == null || archivedUrl.isEmpty()) return null;
        try {
            URL parsed = new URL(archivedUrl.replace("&amp;", "&"));
            String host = parsed.getHost().toLowerCase(Locale.US);
            if (!"preview.redd.it".equals(host) && !"i.redd.it".equals(host)) return null;
            String path = parsed.getPath();
            String basename = path.substring(path.lastIndexOf('/') + 1);
            return CDN_FILE.matcher(basename).matches() ? basename : null;
        } catch (Exception ignored) { return null; }
    }

    private static JSONObject fetchObject(String url) throws IOException {
        HttpURLConnection connection = open(new URL(url), "application/json");
        try {
            return new JSONObject(readText(connection, MAX_METADATA_BYTES));
        } catch (JSONException error) {
            throw new IOException("Arctic Shift returned invalid JSON", error);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(URL url, String accept) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol()))
            throw new IOException("Insecure URL blocked");
        String host = url.getHost().toLowerCase(Locale.US);
        if (!"www.reddit.com".equals(host)
                && !"arctic-shift.photon-reddit.com".equals(host)
                && !"i.redd.it".equals(host))
            throw new IOException("Untrusted host blocked: " + host);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Accept-Encoding", "identity");
        int status = connection.getResponseCode();
        if (status != 200) {
            connection.disconnect();
            throw new IOException(host + " returned HTTP " + status);
        }
        return connection;
    }

    private static String readText(HttpURLConnection connection, int limit) throws IOException {
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read, total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("Gallery metadata exceeds size limit");
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Calls the writer only after validating the original JPEG/PNG/GIF/WebP signature. */
    public static void streamOriginal(Photo photo, OriginalWriter writer) throws IOException {
        URL url = new URL(photo.originalUrl);
        if (!"i.redd.it".equalsIgnoreCase(url.getHost()))
            throw new IOException("Image host is not i.redd.it");
        HttpURLConnection connection = open(url, "image/*");
        try (InputStream input = new java.io.BufferedInputStream(connection.getInputStream())) {
            String contentType = canonicalMime(connection.getContentType().split(";")[0]);
            if (!contentType.startsWith("image/"))
                throw new IOException("CDN returned non-image data: " + contentType);
            java.io.BufferedInputStream buffered = (java.io.BufferedInputStream) input;
            buffered.mark(16);
            byte[] header = new byte[16];
            int count = 0;
            while (count < header.length) {
                int n = buffered.read(header, count, header.length - count);
                if (n < 0) break;
                count += n;
            }
            if (!isOriginalSignature(header, count, photo.mime))
                throw new IOException("CDN did not return an original " + photo.mime + " file");
            buffered.reset();
            writer.copyFrom(buffered, MAX_IMAGE_BYTES);
        } finally {
            connection.disconnect();
        }
    }

    public interface OriginalWriter {
        void copyFrom(InputStream source, int maxBytes) throws IOException;
    }

    public static boolean isOriginalSignature(byte[] bytes, int n, String mime) {
        if (bytes == null || n < 12) return false;
        switch (canonicalMime(mime)) {
            case "image/jpeg":
                return (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 &&
                        (bytes[2] & 255) == 255;
            case "image/png":
                return (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 &&
                        bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10 &&
                        bytes[6] == 26 && bytes[7] == 10;
            case "image/gif":
                return bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' &&
                        bytes[3] == '8';
            case "image/webp":
                return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' &&
                        bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' &&
                        bytes[10] == 'B' && bytes[11] == 'P';
            default: return false;
        }
    }
}
