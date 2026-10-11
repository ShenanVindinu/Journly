package uk.ac.wlv.journly;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.security.SecureRandom;

public class TumblrAuth {

    private static final String TAG = "TumblrAuth";

    // Must be exactly the redirect URL you registered on Tumblr
    public static final String REDIRECT_URI = "https://github.com/ShenanVindinu/Journly";

    private static final String AUTHORIZE_URL = "https://www.tumblr.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.tumblr.com/v2/oauth2/token";

    private static final String PREFS = "journly_tumblr";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES = "expires_at";

    private final SharedPreferences prefs;

    public TumblrAuth(Context context) {
        // MODE_PRIVATE: only this app can read the file
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // Random value sent to Tumblr and checked on return, so a forged redirect is rejected
    public static String newState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    // The address of Tumblr's login and permission page
    public String buildAuthorizeUrl(String state) {
        return Uri.parse(AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("client_id", BuildConfig.TUMBLR_CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "write offline_access")
                .appendQueryParameter("state", state)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .build().toString();
    }

    public boolean isConnected() {
        return prefs.getString(KEY_REFRESH, null) != null
                || prefs.getString(KEY_ACCESS, null) != null;
    }

    // Swap the one-time code for tokens. Background thread only.
    public boolean exchangeCode(String code) {
        MultipartBody body = new MultipartBody()
                .addField("grant_type", "authorization_code")
                .addField("code", code)
                .addField("client_id", BuildConfig.TUMBLR_CLIENT_ID)
                .addField("client_secret", BuildConfig.TUMBLR_CLIENT_SECRET)
                .addField("redirect_uri", REDIRECT_URI);
        return requestTokens(body);
    }

    // Get a new access token using the refresh token. Background thread only.
    public boolean refresh() {
        String refreshToken = prefs.getString(KEY_REFRESH, null);
        if (refreshToken == null) {
            return false;
        }
        MultipartBody body = new MultipartBody()
                .addField("grant_type", "refresh_token")
                .addField("refresh_token", refreshToken)
                .addField("client_id", BuildConfig.TUMBLR_CLIENT_ID)
                .addField("client_secret", BuildConfig.TUMBLR_CLIENT_SECRET);
        return requestTokens(body);
    }

    // A token that is still valid, refreshing it first if it has expired.
    // Returns null if the user needs to connect again. Background thread only.
    public String getValidAccessToken() {
        String access = prefs.getString(KEY_ACCESS, null);
        long expiresAt = prefs.getLong(KEY_EXPIRES, 0);
        if (access != null && System.currentTimeMillis() < expiresAt) {
            return access;
        }
        if (refresh()) {
            return prefs.getString(KEY_ACCESS, null);
        }
        return null;
    }

    public void disconnect() {
        prefs.edit().clear().apply();
    }

    private boolean requestTokens(MultipartBody body) {
        try {
            Http.Response r = Http.post(TOKEN_URL, null, body);
            if (!r.ok()) {
                // Only the error is logged, never the tokens
                Log.e(TAG, "Token request failed: " + r.code + " " + r.body);
                return false;
            }
            JSONObject json = new JSONObject(r.body);
            String access = json.getString("access_token");
            // Tumblr gives a new refresh token each time, keep the old one if none arrives
            String refresh = json.optString("refresh_token",
                    prefs.getString(KEY_REFRESH, null));
            long expiresIn = json.optLong("expires_in", 2400); // seconds
            long expiresAt = System.currentTimeMillis() + expiresIn * 1000 - 60000;

            prefs.edit()
                    .putString(KEY_ACCESS, access)
                    .putString(KEY_REFRESH, refresh)
                    .putLong(KEY_EXPIRES, expiresAt)
                    .apply();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Token request error", e);
            return false;
        }
    }
}