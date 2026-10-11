package uk.ac.wlv.journly;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectTumblrActivity extends AppCompatActivity {

    private TumblrAuth auth;
    private WebView webView;
    private View layoutIntro;
    private ProgressBar progress;
    private String expectedState;   // the state we sent, checked when Tumblr redirects back
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Tumblr's login page needs JavaScript. We only ever load Tumblr's own pages here.
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_tumblr);

        auth = new TumblrAuth(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        layoutIntro = findViewById(R.id.layoutIntro);
        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);
        Button buttonConnect = findViewById(R.id.buttonConnect);
        TextView textNotNow = findViewById(R.id.textNotNow);

        buttonConnect.setOnClickListener(v -> startLogin());
        textNotNow.setOnClickListener(v -> finish());

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            // Catch the moment Tumblr sends the user back to our redirect address
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri.toString().startsWith(TumblrAuth.REDIRECT_URI)) {
                    handleRedirect(uri);
                    return true; // do not load the page
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (webView.getVisibility() == View.VISIBLE) {
                    progress.setVisibility(View.GONE);
                }
            }
        });
    }

    private void startLogin() {
        expectedState = TumblrAuth.newState();
        layoutIntro.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        webView.loadUrl(auth.buildAuthorizeUrl(expectedState));
    }

    private void handleRedirect(Uri uri) {
        webView.stopLoading();
        webView.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);

        String code = uri.getQueryParameter("code");
        String state = uri.getQueryParameter("state");

        if (uri.getQueryParameter("error") != null || code == null) {
            showIntroAgain("Login was cancelled");
            return;
        }
        // Reject the answer if it does not carry the state we sent
        if (expectedState == null || !expectedState.equals(state)) {
            showIntroAgain("Login failed, please try again");
            return;
        }

        // The network call must not run on the main thread
        executor.execute(() -> {
            boolean ok = auth.exchangeCode(code);
            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(this, "Connected to Tumblr",
                            Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    showIntroAgain("Could not connect to Tumblr");
                }
            });
        });
    }

    private void showIntroAgain(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        progress.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        layoutIntro.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        webView.destroy();
        super.onDestroy();
    }
}