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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageDetailActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private TumblrAuth tumblrAuth;
    private long messageId;
    private Message message;

    private ImageView imageMessage;
    private TextView textContent;
    private TextView textDate;
    private TextView textUploaded;
    private Button buttonUpload;

    // Network work runs on this background thread
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE, d MMM yyyy - h:mm a", Locale.getDefault());

    // After the user connects Tumblr, carry straight on to the upload
    private final ActivityResultLauncher<Intent> connectLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            confirmUpload();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_detail);

        dbHelper = new DatabaseHelper(this);
        tumblrAuth = new TumblrAuth(this);

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
        textUploaded = findViewById(R.id.textUploaded);
        buttonUpload = findViewById(R.id.buttonUpload);
        ImageButton buttonBack = findViewById(R.id.buttonBack);
        ImageButton buttonEdit = findViewById(R.id.buttonEdit);
        ImageButton buttonDelete = findViewById(R.id.buttonDelete);
        Button buttonShare = findViewById(R.id.buttonShare);

        buttonBack.setOnClickListener(v -> finish());

        // Edit reuses the New Message screen, in edit mode
        buttonEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, MessageEditActivity.class);
            intent.putExtra(MessageEditActivity.EXTRA_MESSAGE_ID, messageId);
            startActivity(intent);
        });

        buttonDelete.setOnClickListener(v -> confirmDelete());
        buttonShare.setOnClickListener(v -> shareMessage());
        buttonUpload.setOnClickListener(v -> startUpload());

        // Open the published post in the browser
        textUploaded.setOnClickListener(v -> {
            if (message != null && message.getUploadedUrl() != null) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(message.getUploadedUrl())));
            }
        });
    }

    // Reload every time we return, so edits show up straight away
    @Override
    protected void onResume() {
        super.onResume();
        loadMessage();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown(); // lets a running upload finish
        super.onDestroy();
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

        boolean published = message.isUploaded() && message.getUploadedUrl() != null;
        textUploaded.setVisibility(published ? View.VISIBLE : View.GONE);
    }

    // ---------- Upload to Tumblr ----------

    private void startUpload() {
        if (!tumblrAuth.isConnected()) {
            connectLauncher.launch(new Intent(this, ConnectTumblrActivity.class));
        } else {
            confirmUpload();
        }
    }

    // Publishing is public, so always ask first
    private void confirmUpload() {
        String text = message.isUploaded()
                ? "This message was already uploaded. Upload it again?"
                : "This message will be published on your Tumblr blog, "
                  + "and anyone can see it.";
        new AlertDialog.Builder(this)
                .setTitle("Publish to Tumblr")
                .setMessage(text)
                .setPositiveButton("Publish", (dialog, which) -> doUpload())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doUpload() {
        setUploading(true);
        final Message toUpload = message;

        executor.execute(() -> {
            try {
                TumblrUploader uploader = new TumblrUploader(this);
                String token = uploader.getToken();
                String blog = uploader.getBlogName(token);
                String url = uploader.createPost(token, blog, toUpload);

                // Remember that it was published, and where
                toUpload.setUploaded(true);
                toUpload.setUploadedUrl(url);
                dbHelper.updateMessage(toUpload);

                runOnUiThread(() -> {
                    setUploading(false);
                    loadMessage();
                    Toast.makeText(this, "Published on Tumblr",
                            Toast.LENGTH_LONG).show();
                });
            } catch (TumblrUploader.NeedsLoginException e) {
                runOnUiThread(() -> {
                    setUploading(false);
                    Toast.makeText(this, "Please connect Tumblr again",
                            Toast.LENGTH_LONG).show();
                    connectLauncher.launch(
                            new Intent(this, ConnectTumblrActivity.class));
                });
            } catch (TumblrUploader.UploadException e) {
                String error = e.getMessage();
                runOnUiThread(() -> showUploadError(error));
            } catch (IOException e) {
                runOnUiThread(() -> showUploadError("No internet connection"));
            } catch (Exception e) {
                runOnUiThread(() -> showUploadError("Upload failed"));
            }
        });
    }

    private void setUploading(boolean uploading) {
        buttonUpload.setEnabled(!uploading);
        buttonUpload.setText(uploading ? "Uploading..." : "Upload to Tumblr");
    }

    private void showUploadError(String error) {
        setUploading(false);
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    // ---------- Share ----------

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

    // ---------- Delete ----------

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