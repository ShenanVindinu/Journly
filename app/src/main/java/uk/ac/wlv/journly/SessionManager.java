package uk.ac.wlv.journly;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS_NAME = "journly_session";
    private static final String KEY_USER_ID = "user_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        // MODE_PRIVATE: only this app can read the file
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Remembers who is logged in (only the id, never the password)
    public void createSession(long userId) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getLong(KEY_USER_ID, -1) != -1;
    }

    public long getCurrentUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public void logout() {
        prefs.edit().remove(KEY_USER_ID).apply();
    }
}
