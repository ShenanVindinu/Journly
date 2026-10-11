package uk.ac.wlv.journly;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "journly.db";
    private static final int DB_VERSION = 1;

    // Users table: passwords are stored as a salted hash, never as plain text
    private static final String CREATE_USERS =
            "CREATE TABLE users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "email TEXT NOT NULL UNIQUE, " +
                    "password_hash TEXT NOT NULL, " +
                    "salt TEXT NOT NULL, " +
                    "created_at INTEGER)";

    // Messages table: each message belongs to one user.
    // SQLite has no BOOLEAN or TIMESTAMP type, so we use INTEGER for both.
    private static final String CREATE_MESSAGES =
            "CREATE TABLE messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "image_path TEXT, " +
                    "created_at INTEGER, " +
                    "updated_at INTEGER, " +
                    "is_uploaded INTEGER DEFAULT 0, " +
                    "uploaded_url TEXT, " +
                    "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // SQLite ignores foreign keys unless we switch them on
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USERS);     // users first, messages refer to it
        db.execSQL(CREATE_MESSAGES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS messages");
        db.execSQL("DROP TABLE IF EXISTS users");
        onCreate(db);
    }

    // ---------- Users ----------

    // Returns the new row id, or -1 if the email is already registered
    public long insertUser(User u) {
        ContentValues v = new ContentValues();
        v.put("email", u.getEmail());
        v.put("password_hash", u.getPasswordHash());
        v.put("salt", u.getSalt());
        v.put("created_at", u.getCreatedAt());
        return getWritableDatabase().insert("users", null, v);
    }

    // Returns null if no user has that email
    public User getUserByEmail(String email) {
        Cursor c = getReadableDatabase().query("users", null,
                "email = ?", new String[]{email}, null, null, null);
        User u = null;
        if (c.moveToFirst()) {
            u = new User();
            u.setId(c.getLong(c.getColumnIndexOrThrow("id")));
            u.setEmail(c.getString(c.getColumnIndexOrThrow("email")));
            u.setPasswordHash(c.getString(c.getColumnIndexOrThrow("password_hash")));
            u.setSalt(c.getString(c.getColumnIndexOrThrow("salt")));
            u.setCreatedAt(c.getLong(c.getColumnIndexOrThrow("created_at")));
        }
        c.close();
        return u;
    }

    // ---------- Messages ----------

    // Turn a Message object into column values
    private ContentValues toValues(Message m) {
        ContentValues v = new ContentValues();
        v.put("user_id", m.getUserId());
        v.put("content", m.getContent());
        v.put("image_path", m.getImagePath());
        v.put("created_at", m.getCreatedAt());
        v.put("updated_at", m.getUpdatedAt());
        v.put("is_uploaded", m.isUploaded() ? 1 : 0);
        v.put("uploaded_url", m.getUploadedUrl());
        return v;
    }

    // Turn the current cursor row into a Message object
    private Message fromCursor(Cursor c) {
        Message m = new Message();
        m.setId(c.getLong(c.getColumnIndexOrThrow("id")));
        m.setUserId(c.getLong(c.getColumnIndexOrThrow("user_id")));
        m.setContent(c.getString(c.getColumnIndexOrThrow("content")));
        m.setImagePath(c.getString(c.getColumnIndexOrThrow("image_path")));
        m.setCreatedAt(c.getLong(c.getColumnIndexOrThrow("created_at")));
        m.setUpdatedAt(c.getLong(c.getColumnIndexOrThrow("updated_at")));
        m.setUploaded(c.getInt(c.getColumnIndexOrThrow("is_uploaded")) == 1);
        m.setUploadedUrl(c.getString(c.getColumnIndexOrThrow("uploaded_url")));
        return m;
    }

    public long insertMessage(Message m) {
        return getWritableDatabase().insert("messages", null, toValues(m));
    }

    public boolean updateMessage(Message m) {
        int rows = getWritableDatabase().update("messages", toValues(m),
                "id = ?", new String[]{String.valueOf(m.getId())});
        return rows > 0;
    }

    public boolean deleteMessage(long id) {
        int rows = getWritableDatabase().delete("messages",
                "id = ?", new String[]{String.valueOf(id)});
        return rows > 0;
    }

    // Delete a group of messages in one transaction
    public boolean deleteMessages(List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (long id : ids) {
                db.delete("messages", "id = ?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    // All messages of one user, newest first
    public List<Message> getAllMessages(long userId) {
        return query("user_id = ?", new String[]{String.valueOf(userId)});
    }

    // The ? placeholders stop SQL injection (Lecture 7)
    public List<Message> searchMessages(long userId, String text) {
        return query("user_id = ? AND content LIKE ?",
                new String[]{String.valueOf(userId), "%" + text + "%"});
    }

    public Message getMessageById(long id) {
        List<Message> result = query("id = ?", new String[]{String.valueOf(id)});
        return result.isEmpty() ? null : result.get(0);
    }

    // Shared helper used by the methods above
    private List<Message> query(String selection, String[] args) {
        List<Message> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null,
                selection, args, null, null, "created_at DESC");
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }
}
