package uk.ac.wlv.journly;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

// Creates posts on the user's Tumblr blog. Always call it from a background thread.
public class TumblrUploader {

    private static final String TAG = "TumblrUploader";
    private static final String API = "https://api.tumblr.com/v2";

    // The user has to connect their Tumblr account again
    public static class NeedsLoginException extends Exception {
    }

    // Tumblr refused something. The message is safe to show to the user.
    public static class UploadException extends Exception {
        public UploadException(String message) {
            super(message);
        }
    }

    private final TumblrAuth auth;

    public TumblrUploader(Context context) {
        auth = new TumblrAuth(context);
    }

    // A working access token (refreshed if it expired)
    public String getToken() throws NeedsLoginException {
        String token = auth.getValidAccessToken();
        if (token == null) {
            auth.disconnect();
            throw new NeedsLoginException();
        }
        return token;
    }

    // The name of the user's main blog
    public String getBlogName(String token)
            throws IOException, NeedsLoginException, UploadException {
        Http.Response r = Http.get(API + "/user/info", token);
        if (r.code == 401) {
            auth.disconnect();
            throw new NeedsLoginException();
        }
        if (!r.ok()) {
            Log.e(TAG, "User info failed: " + r.code + " " + r.body);
            throw new UploadException("Could not read your Tumblr account");
        }
        try {
            JSONArray blogs = new JSONObject(r.body)
                    .getJSONObject("response")
                    .getJSONObject("user")
                    .getJSONArray("blogs");
            // Prefer the primary blog
            for (int i = 0; i < blogs.length(); i++) {
                JSONObject blog = blogs.getJSONObject(i);
                if (blog.optBoolean("primary", false)) {
                    return blog.getString("name");
                }
            }
            if (blogs.length() > 0) {
                return blogs.getJSONObject(0).getString("name");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not read blogs", e);
        }
        throw new UploadException("No Tumblr blog found on this account");
    }

    // Creates one post (photo first, then the text). Returns the address of the post.
    public String createPost(String token, String blogName, Message m)
            throws IOException, NeedsLoginException, UploadException {
        MultipartBody body = new MultipartBody();
        try {
            JSONArray content = new JSONArray();

            String path = m.getImagePath();
            File imageFile = (path == null) ? null : new File(path);
            boolean hasImage = imageFile != null && imageFile.exists();

            if (hasImage) {
                // The identifier links this block to the file part sent below
                JSONObject media = new JSONObject()
                        .put("type", "image/jpeg")
                        .put("identifier", "photo");
                content.put(new JSONObject()
                        .put("type", "image")
                        .put("media", new JSONArray().put(media)));
            }
            content.put(new JSONObject()
                    .put("type", "text")
                    .put("text", m.getContent()));

            JSONObject json = new JSONObject()
                    .put("content", content)
                    .put("state", "published");

            body.addJson("json", json.toString());
            if (hasImage) {
                body.addFile("photo", "photo.jpg", "image/jpeg", readFile(imageFile));
            }
        } catch (JSONException e) {
            throw new UploadException("Could not prepare the post");
        }

        Http.Response r = Http.post(API + "/blog/" + blogName + "/posts", token, body);

        if (r.code == 401) {
            auth.disconnect();
            throw new NeedsLoginException();
        }
        if (!r.ok()) {
            Log.e(TAG, "Post failed: " + r.code + " " + r.body);
            throw new UploadException("Tumblr refused the post (error " + r.code + ")");
        }

        try {
            JSONObject response = new JSONObject(r.body).getJSONObject("response");
            String id = response.optString("id_string", response.optString("id", ""));
            if (!id.isEmpty()) {
                return "https://www.tumblr.com/" + blogName + "/" + id;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not read the post id", e);
        }
        // The post was created, but we could not read its address
        return "https://www.tumblr.com/" + blogName;
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}