Journly - 6CS027 portfolio
Shenan Vindinu, Wolverhampton ID 2635762

If you just want to try it
Install the APK on Android 7.0 (API 24) or newer. I tested it on a Pixel 10 Pro
emulator running API 37.
Open the app and tap "New here? Register". Use any email and a password with at
least 6 characters, then log in. Accounts only live on the phone, nothing is
sent anywhere.

Uploading to Tumblr
Open a message and tap "Upload to Tumblr". The first time, it asks you to
connect your Tumblr account. Log in with your own Tumblr email and password and
press Allow if it asks. The post goes to your own blog.
If your Tumblr account was made with "Continue with Google", the login inside
the app might not work, so use an account that has an email and password.
Uploading needs internet. Everything else works offline.

If you want to build it yourself
1. Open the Journly folder in Android Studio and wait for Gradle to sync.
2. The Tumblr keys are not in the project, because I kept them out of GitHub.
   Open local.properties (top folder, next to settings.gradle.kts) and add
   these two lines under the sdk.dir line:
   TUMBLR_CLIENT_ID=your key
   TUMBLR_CLIENT_SECRET=your secret
   You need to register your own app at tumblr.com/oauth/apps to get them.
   Put https://github.com/ShenanVindinu/Journly as both the default callback
   URL and the OAuth2 redirect URL, because the app checks for that exact
   address.
3. Run the app. If you skip step 2 it still builds and everything works
   except the Tumblr upload.