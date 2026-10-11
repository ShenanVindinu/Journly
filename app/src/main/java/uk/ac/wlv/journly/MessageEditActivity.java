package uk.ac.wlv.journly;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MessageEditActivity extends AppCompatActivity {

    // Pass this extra to edit an existing message. Without it we create a new one.
    public static final String EXTRA_MESSAGE_ID = "message_id";

    private static final String STATE_IMAGE = "state_image";
    private static final String STATE_CAMERA = "state_camera";
    private static final int MAX_IMAGE_SIZE = 1280;

    private DatabaseHelper dbHelper;
    private long userId;
    private Message currentMessage;   // null when creating a new message
    private String imagePath;         // path of the attached image, or null
    private File cameraFile;          // file the camera app is writing to

    private TextInputLayout layoutContent;
    private TextInputEditText editContent;
    private ImageView imagePreview;
    private View frameImage;

    // Gallery: the system Photo Picker, no permission needed
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) {
                    return; // user closed the picker
                }
                String path = copyToPrivateStorage(uri);
                if (path == null) {
                    Toast.makeText(this, "Could not load that image",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                imagePath = path;
                showImage();
            });

    // Camera: the system camera app writes the photo into cameraFile
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraFile != null) {
                    ImageUtils.shrink(cameraFile, MAX_IMAGE_SIZE);
                    imagePath = cameraFile.getAbsolutePath();
                    showImage();
                } else if (cameraFile != null) {
                    cameraFile.delete(); // photo cancelled, remove the empty file
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_edit);

        dbHelper = new DatabaseHelper(this);
        userId = new SessionManager(this).getCurrentUserId();

        // Keep the header below the status bar, the button above the nav bar and keyboard
        View header = findViewById(R.id.header);
        final int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets bottom = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            v.setPadding(0, 0, 0, bottom.bottom);
            return insets;
        });

        layoutContent = findViewById(R.id.layoutContent);
        editContent = findViewById(R.id.editContent);
        imagePreview = findViewById(R.id.imagePreview);
        frameImage = findViewById(R.id.frameImage);
        TextView textTitle = findViewById(R.id.textTitle);
        ImageButton buttonBack = findViewById(R.id.buttonBack);
        ImageButton buttonRemoveImage = findViewById(R.id.buttonRemoveImage);
        Button buttonGallery = findViewById(R.id.buttonGallery);
        Button buttonCamera = findViewById(R.id.buttonCamera);
        Button buttonSave = findViewById(R.id.buttonSave);

        buttonBack.setOnClickListener(v -> finish());
        buttonGallery.setOnClickListener(v -> galleryLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        buttonCamera.setOnClickListener(v -> launchCamera());
        buttonRemoveImage.setOnClickListener(v -> {
            imagePath = null;
            showImage();
        });
        buttonSave.setOnClickListener(v -> saveMessage());

        // Restore state after a rotation (Lecture 2: onSaveInstanceState)
        if (savedInstanceState != null) {
            imagePath = savedInstanceState.getString(STATE_IMAGE);
            String cameraPath = savedInstanceState.getString(STATE_CAMERA);
            if (cameraPath != null) {
                cameraFile = new File(cameraPath);
            }
        }

        // Edit mode: load the existing message
        long messageId = getIntent().getLongExtra(EXTRA_MESSAGE_ID, -1);
        if (messageId != -1) {
            currentMessage = dbHelper.getMessageById(messageId);
            if (currentMessage != null) {
                textTitle.setText("Edit Message");
                if (savedInstanceState == null) {
                    editContent.setText(currentMessage.getContent());
                    imagePath = currentMessage.getImagePath();
                }
            }
        }

        showImage();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_IMAGE, imagePath);
        if (cameraFile != null) {
            outState.putString(STATE_CAMERA, cameraFile.getAbsolutePath());
        }
    }

    // Show or hide the photo preview
    private void showImage() {
        if (imagePath != null && new File(imagePath).exists()) {
            imagePreview.setImageURI(Uri.fromFile(new File(imagePath)));
            frameImage.setVisibility(View.VISIBLE);
        } else {
            imagePath = null;
            frameImage.setVisibility(View.GONE);
        }
    }

    // A new file in the app's private images folder
    private File createImageFile() {
        File dir = new File(getFilesDir(), "images");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
    }

    private void launchCamera() {
        cameraFile = createImageFile();
        // FileProvider gives the camera app a safe content:// address for our file
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", cameraFile);
        try {
            cameraLauncher.launch(uri);
        } catch (Exception e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    // Copy the picked image into private storage and shrink it
    private String copyToPrivateStorage(Uri uri) {
        File file = createImageFile();
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            file.delete();
            return null;
        }
        ImageUtils.shrink(file, MAX_IMAGE_SIZE);
        return file.getAbsolutePath();
    }

    private void saveMessage() {
        layoutContent.setError(null);
        String text = editContent.getText().toString().trim();

        // Validate before saving
        if (text.isEmpty()) {
            layoutContent.setError("Write something first");
            return;
        }

        if (currentMessage == null) {
            dbHelper.insertMessage(new Message(userId, text, imagePath));
        } else {
            currentMessage.setContent(text);
            currentMessage.setImagePath(imagePath);
            currentMessage.setUpdatedAt(System.currentTimeMillis());
            dbHelper.updateMessage(currentMessage);
        }
        finish(); // MainActivity reloads the list in onResume
    }
}