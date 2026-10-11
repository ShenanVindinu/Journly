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
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private MessageAdapter adapter;
    private TextInputEditText editSearch;
    private TextView textEmpty;
    private TextView textTitle;
    private ImageButton buttonProfile;
    private ImageButton buttonCloseSelection;
    private ImageButton buttonUploadSelected;
    private ImageButton buttonDeleteSelected;
    private FloatingActionButton fabAdd;
    private OnBackPressedCallback selectionBackCallback;
    private long userId;

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
        // TEMPORARY: group upload is built after the single upload works
        buttonUploadSelected.setOnClickListener(v -> Toast.makeText(this,
                "Group upload comes later", Toast.LENGTH_SHORT).show());
    }

    // Reload when we come back from another screen
    @Override
    protected void onResume() {
        super.onResume();
        if (dbHelper != null) {
            loadMessages();
        }
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