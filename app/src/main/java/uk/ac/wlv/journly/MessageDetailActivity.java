package uk.ac.wlv.journly;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MessageDetailActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private long messageId;
    private Message message;

    private ImageView imageMessage;
    private TextView textContent;
    private TextView textDate;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE, d MMM yyyy - h:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_detail);

        dbHelper = new DatabaseHelper(this);

        // Which message to show (passed in by the list screen)
        messageId = getIntent().getLongExtra(MessageEditActivity.EXTRA_MESSAGE_ID, -1);
        if (messageId == -1) {
            finish();
            return;
        }

        // Keep the header below the status bar and the buttons above the nav bar
        View header = findViewById(R.id.header);
        final int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });

        imageMessage = findViewById(R.id.imageMessage);
        textContent = findViewById(R.id.textContent);
        textDate = findViewById(R.id.textDate);
        ImageButton buttonBack = findViewById(R.id.buttonBack);
        ImageButton buttonEdit = findViewById(R.id.buttonEdit);
        ImageButton buttonDelete = findViewById(R.id.buttonDelete);
        Button buttonShare = findViewById(R.id.buttonShare);
        Button buttonUpload = findViewById(R.id.buttonUpload);

        buttonBack.setOnClickListener(v -> finish());

        // Edit reuses the New Message screen, in edit mode
        buttonEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, MessageEditActivity.class);
            intent.putExtra(MessageEditActivity.EXTRA_MESSAGE_ID, messageId);
            startActivity(intent);
        });

        buttonDelete.setOnClickListener(v -> confirmDelete());
        buttonShare.setOnClickListener(v -> shareMessage());

        // TEMPORARY: the real upload is built in Step 9
        buttonUpload.setOnClickListener(v -> Toast.makeText(this,
                "Upload comes in Step 9", Toast.LENGTH_SHORT).show());
    }

    // Reload every time we return, so edits show up straight away
    @Override
    protected void onResume() {
        super.onResume();
        loadMessage();
    }

    private void loadMessage() {
        message = dbHelper.getMessageById(messageId);
        if (message == null) {
            finish(); // the message no longer exists
            return;
        }

        textContent.setText(message.getContent());
        textDate.setText(dateFormat.format(new Date(message.getCreatedAt())));

        String path = message.getImagePath();
        if (path != null && new File(path).exists()) {
            imageMessage.setImageURI(Uri.fromFile(new File(path)));
            imageMessage.setVisibility(View.VISIBLE);
        } else {
            imageMessage.setVisibility(View.GONE);
        }
    }

    // Share the text, and the photo if there is one, with the Android Share intent
    private void shareMessage() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Journly message");
        intent.putExtra(Intent.EXTRA_TEXT, message.getContent());

        String path = message.getImagePath();
        File imageFile = (path == null) ? null : new File(path);

        if (imageFile != null && imageFile.exists()) {
            // FileProvider gives the other app a safe address for our private file
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", imageFile);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri("image", uri));
            // Let the receiving app read this one file, nothing else
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setType("text/plain");
        }

        startActivity(Intent.createChooser(intent, "Share message via"));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete message")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.deleteMessage(messageId);
                    // Also remove the photo file so it does not stay on the phone
                    String path = message.getImagePath();
                    if (path != null) {
                        new File(path).delete();
                    }
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}