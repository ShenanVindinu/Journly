package uk.ac.wlv.journly;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPassword;
    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If someone is already logged in, skip this screen
        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            openMessageList();
            return;
        }

        setContentView(R.layout.activity_login);
        dbHelper = new DatabaseHelper(this);

        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        Button buttonLogin = findViewById(R.id.buttonLogin);
        TextView textRegister = findViewById(R.id.textRegister);

        buttonLogin.setOnClickListener(v -> attemptLogin());
        textRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        // Clear errors from the last attempt
        layoutEmail.setError(null);
        layoutPassword.setError(null);

        String email = editEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        String password = editPassword.getText().toString();

        if (email.isEmpty()) {
            layoutEmail.setError("Enter your email");
            return;
        }
        if (password.isEmpty()) {
            layoutPassword.setError("Enter your password");
            return;
        }

        User user = dbHelper.getUserByEmail(email);

        // Same message for wrong email and wrong password, so we do not
        // reveal which emails are registered
        if (user == null
                || !PasswordHasher.verify(password, user.getSalt(), user.getPasswordHash())) {
            layoutPassword.setError("Wrong email or password");
            return;
        }

        sessionManager.createSession(user.getId());
        openMessageList();
    }

    private void openMessageList() {
        startActivity(new Intent(this, MainActivity.class));
        finish(); // so Back does not return to the login screen
    }
}