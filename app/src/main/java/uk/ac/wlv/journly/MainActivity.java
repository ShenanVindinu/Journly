package uk.ac.wlv.journly;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private MessageAdapter adapter;
    private TextInputEditText editSearch;
    private TextView textEmpty;
    private TextView textTitle;
    private View buttonProfile;
    private ImageButton buttonCloseSelection;
    private ImageButton buttonUploadSelected;
    private ImageButton buttonDeleteSelected;
    private FloatingActionButton fabAdd;
    private OnBackPressedCallback selectionBackCallback;
    private long userId;

    // Group upload: the messages waiting to be published, and how many were skipped
    private List<Message> pendingUpload;
    private int skippedCount;

    // Network work runs on this background thread
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // After the user connects Tumblr, carry straight on with the group upload
    private final ActivityResultLauncher<Intent> connectLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && pendingUpload != null) {
                            confirmGroupUpload();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nobody logged in? Send them to the login screen
        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }
        userId = sessionManager.getCurrentUserId();

        setContentView(R.layout.activity_main);
        dbHelper = new DatabaseHelper(this);

        // Keep the header below the status bar and the list above the nav bar
        View header = findViewById(R.id.header);
        final int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });

        textEmpty = findViewById(R.id.textEmpty);
        textTitle = findViewById(R.id.textTitle);
        editSearch = findViewById(R.id.editSearch);
        buttonProfile = findViewById(R.id.buttonProfile);
        buttonCloseSelection = findViewById(R.id.buttonCloseSelection);
        buttonUploadSelected = findViewById(R.id.buttonUploadSelected);
        buttonDeleteSelected = findViewById(R.id.buttonDeleteSelected);
        fabAdd = findViewById(R.id.fabAdd);

        // While selecting, the Back button leaves selection mode instead of closing the app
        selectionBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                adapter.clearSelection();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, selectionBackCallback);

        // The list
        RecyclerView recycler = findViewById(R.id.recyclerMessages);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(new MessageAdapter.Listener() {
            @Override
            public void onMessageClick(Message message) {
                Intent intent = new Intent(MainActivity.this, MessageDetailActivity.class);
                intent.putExtra(MessageEditActivity.EXTRA_MESSAGE_ID, message.getId());
                startActivity(intent);
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                updateSelectionUi(selectedCount);
            }
        });
        recycler.setAdapter(adapter);

        // Search as the user types
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loadMessages();
            }
        });

        buttonProfile.setOnClickListener(v -> confirmLogout());
        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, MessageEditActivity.class)));

        buttonCloseSelection.setOnClickListener(v -> adapter.clearSelection());
        buttonDeleteSelected.setOnClickListener(v -> confirmDeleteSelected());
        buttonUploadSelected.setOnClickListener(v -> startGroupUpload());
    }

    // Reload when we come back from another screen
    @Override
    protected void onResume() {
        super.onResume();
        if (dbHelper != null) {
            loadMessages();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdown(); // lets a running upload finish
        super.onDestroy();
    }

    // Load all messages, or only the matching ones if the search box has text
    private void loadMessages() {
        String query = editSearch.getText().toString().trim();
        List<Message> list;
        if (query.isEmpty()) {
            list = dbHelper.getAllMessages(userId);
        } else {
            list = dbHelper.searchMessages(userId, query);
        }
        adapter.setMessages(list);

        textEmpty.setText(query.isEmpty()
                ? "No messages yet. Tap + to write one."
                : "No messages match your search.");
        textEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // Switch the header between normal mode and selection mode
    private void updateSelectionUi(int count) {
        boolean selecting = count > 0;

        selectionBackCallback.setEnabled(selecting);
        textTitle.setText(selecting ? count + " selected" : "Journly");
        buttonCloseSelection.setVisibility(selecting ? View.VISIBLE : View.GONE);
        buttonUploadSelected.setVisibility(selecting ? View.VISIBLE : View.GONE);
        buttonDeleteSelected.setVisibility(selecting ? View.VISIBLE : View.GONE);
        buttonProfile.setVisibility(selecting ? View.GONE : View.VISIBLE);

        if (selecting) {
            fabAdd.hide();
        } else {
            fabAdd.show();
        }
    }

    // ---------- Group upload to Tumblr ----------

    private void startGroupUpload() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.isEmpty()) {
            return;
        }

        // Skip messages that are already published, so Tumblr gets no duplicates
        List<Message> toUpload = new ArrayList<>();
        for (Message m : selected) {
            if (!m.isUploaded()) {
                toUpload.add(m);
            }
        }
        skippedCount = selected.size() - toUpload.size();

        if (toUpload.isEmpty()) {
            Toast.makeText(this, "These messages are already published",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        pendingUpload = toUpload;
        if (!new TumblrAuth(this).isConnected()) {
            connectLauncher.launch(new Intent(this, ConnectTumblrActivity.class));
        } else {
            confirmGroupUpload();
        }
    }

    // Publishing is public, so always ask first
    private void confirmGroupUpload() {
        if (pendingUpload == null || pendingUpload.isEmpty()) {
            return;
        }
        final List<Message> toUpload = pendingUpload;
        int count = toUpload.size();

        String text = "They will be published on your Tumblr blog, "
                + "and anyone can see them.";
        if (skippedCount > 0) {
            text += "\n\n" + skippedCount + " already published, skipped.";
        }

        new AlertDialog.Builder(this)
                .setTitle("Publish " + count + (count == 1 ? " message" : " messages"))
                .setMessage(text)
                .setPositiveButton("Publish", (dialog, which) -> doGroupUpload(toUpload))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doGroupUpload(List<Message> messages) {
        pendingUpload = null;

        // Oldest first, so the newest message ends up on top of the blog
        final List<Message> ordered = new ArrayList<>(messages);
        Collections.reverse(ordered);

        final AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Publishing to Tumblr")
                .setMessage("Starting...")
                .setCancelable(false)
                .show();

        executor.execute(() -> {
            int done = 0;
            String problem = null;
            boolean needsLogin = false;

            try {
                TumblrUploader uploader = new TumblrUploader(this);
                String token = uploader.getToken();
                String blog = uploader.getBlogName(token);

                for (int i = 0; i < ordered.size(); i++) {
                    final int number = i + 1;
                    runOnUiThread(() -> progressDialog.setMessage(
                            "Uploading " + number + " of " + ordered.size() + "..."));

                    Message m = ordered.get(i);
                    try {
                        String url = uploader.createPost(token, blog, m);
                        // Remember that it was published, and where
                        m.setUploaded(true);
                        m.setUploadedUrl(url);
                        dbHelper.updateMessage(m);
                        done++;
                    } catch (TumblrUploader.UploadException e) {
                        problem = e.getMessage(); // skip this one, try the next
                    }
                }
            } catch (TumblrUploader.NeedsLoginException e) {
                needsLogin = true;
            } catch (IOException e) {
                problem = "No internet connection";
            } catch (Exception e) {
                problem = "Upload failed";
            }

            final int finalDone = done;
            final String finalProblem = problem;
            final boolean finalNeedsLogin = needsLogin;

            runOnUiThread(() -> {
                progressDialog.dismiss();
                loadMessages(); // also clears the selection

                String result = finalDone + " of " + ordered.size()
                        + " published on Tumblr";
                if (finalProblem != null && finalDone < ordered.size()) {
                    result += " (" + finalProblem + ")";
                }
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();

                // The login expired: connect again, then offer the rest
                if (finalNeedsLogin) {
                    pendingUpload = new ArrayList<>();
                    for (Message m : ordered) {
                        if (!m.isUploaded()) {
                            pendingUpload.add(m);
                        }
                    }
                    skippedCount = 0;
                    connectLauncher.launch(
                            new Intent(this, ConnectTumblrActivity.class));
                }
            });
        });
    }

    // ---------- Delete ----------

    // Ask before deleting the selected group
    private void confirmDeleteSelected() {
        List<Message> selected = adapter.getSelectedMessages();
        int count = selected.size();
        if (count == 0) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete " + count + (count == 1 ? " message" : " messages"))
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteMessages(selected))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMessages(List<Message> selected) {
        List<Long> ids = new ArrayList<>();
        for (Message m : selected) {
            ids.add(m.getId());
        }

        // Delete from the database first, then remove the photo files
        if (dbHelper.deleteMessages(ids)) {
            for (Message m : selected) {
                String path = m.getImagePath();
                if (path != null) {
                    new File(path).delete();
                }
            }
            Toast.makeText(this, ids.size() + " deleted", Toast.LENGTH_SHORT).show();
        }
        loadMessages();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Do you want to log out?")
                .setPositiveButton("Log out", (dialog, which) -> {
                    sessionManager.logout();
                    goToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}