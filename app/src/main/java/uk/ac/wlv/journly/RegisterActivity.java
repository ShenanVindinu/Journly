package uk.ac.wlv.journly;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPassword;
    private TextInputLayout layoutConfirm;
    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private TextInputEditText editConfirm;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        dbHelper = new DatabaseHelper(this);

        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutConfirm = findViewById(R.id.layoutConfirm);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        editConfirm = findViewById(R.id.editConfirm);
        Button buttonRegister = findViewById(R.id.buttonRegister);
        TextView textBackToLogin = findViewById(R.id.textBackToLogin);

        buttonRegister.setOnClickListener(v -> attemptRegister());
        textBackToLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        layoutEmail.setError(null);
        layoutPassword.setError(null);
        layoutConfirm.setError(null);

        String email = editEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        String password = editPassword.getText().toString();
        String confirm = editConfirm.getText().toString();

        // Validate all input before touching the database
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError("Enter a valid email");
            return;
        }
        if (password.length() < 6) {
            layoutPassword.setError("At least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            layoutConfirm.setError("Passwords do not match");
            return;
        }
        if (dbHelper.getUserByEmail(email) != null) {
            layoutEmail.setError("This email is already registered");
            return;
        }

        // Store a salted hash, never the password itself
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);
        long id = dbHelper.insertUser(new User(email, hash, salt));

        if (id == -1) {
            layoutEmail.setError("Could not create the account");
            return;
        }

        Toast.makeText(this, "Account created. Please log in.",
                Toast.LENGTH_SHORT).show();
        finish(); // back to the login screen
    }
}