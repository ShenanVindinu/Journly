package uk.ac.wlv.journly;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private MessageAdapter adapter;
    private TextInputEditText editSearch;
    private TextView textEmpty;
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
        editSearch = findViewById(R.id.editSearch);
        ImageButton buttonProfile = findViewById(R.id.buttonProfile);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        // The list
        RecyclerView recycler = findViewById(R.id.recyclerMessages);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(new MessageAdapter.Listener() {
            @Override
            public void onMessageClick(Message message) {
                // Opens the detail screen in Step 7
                Toast.makeText(MainActivity.this, "Open message (Step 7)",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMessageLongClick(Message message) {
                // Selection mode for group delete comes in Step 8
                Toast.makeText(MainActivity.this, "Select mode (Step 8)",
                        Toast.LENGTH_SHORT).show();
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

        // TEMPORARY: adds a test message so we can see the list. Replaced in Step 6.
        fabAdd.setOnClickListener(v -> {
            Message m = new Message(userId,
                    "Test message " + (System.currentTimeMillis() % 1000), null);
            dbHelper.insertMessage(m);
            loadMessages();
        });
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