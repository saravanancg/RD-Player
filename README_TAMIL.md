# RD PLAYER – Android Source Project

இந்த ZIP என்பது RD PLAYER-ன் Android source project. இதில் local media scan/playback-க்கான அடித்தளம், YouTube Data API search integration, official embedded YouTube player UI மற்றும் GitHub Actions cloud build workflow உள்ளன.

## முக்கியமான நிலை
இந்த source package இங்கே Android SDK/Gradle மூலம் compile செய்து சோதிக்கப்படவில்லை. எனவே இதை "tested final APK" என்று கருத வேண்டாம். APK-ஐ GitHub Actions cloud build மூலம் உருவாக்குவதற்கான workflow சேர்க்கப்பட்டுள்ளது.

## Android app-ல் உள்ளவை
- Kotlin + Jetpack Compose dark/purple interface
- Phone MediaStore-ல் audio/video பட்டியல் பெறும் permission flow
- Media3 ExoPlayer மூலம் local media playback
- YouTube Data API v3 search code (API key தேவை)
- Official YouTube embedded player WebView

## இன்னும் செய்ய வேண்டிய production பணிகள்
- MediaSessionService + persistent background audio + lock-screen/notification controls
- Playlist/Favorites/Recently Played-ஐ Room database-ல் நிரந்தரமாக சேமித்தல்
- YouTube API key-ஐ Android client-ல் வைக்காமல் backend proxy மூலம் பாதுகாத்தல்
- பல Android சாதனங்களில் சோதனை, accessibility, crash handling, release signing

## YouTube API key
YouTube search செயல்பட YouTube Data API v3 key தேவை.
`app/build.gradle.kts`-ல் `YOUTUBE_API_KEY` placeholder உள்ளது. Public GitHub repository-ல் key-ஐ source-ல் நேரடியாக சேர்க்க வேண்டாம். Production-ல் backend proxy பயன்படுத்தவும்.

## Android Studio இல்லாமல் APK உருவாக்கும் வழி (GitHub Actions)
1. GitHub-ல் உங்களுடைய account-இல் புதிய repository உருவாக்கவும்.
2. இந்த ZIP-ஐ extract செய்து உள்ளே உள்ள files அனைத்தையும் repository-க்கு upload செய்யவும்.
3. GitHub repository → Actions tab திறக்கவும்.
4. `Build RD Player APK` workflow தேர்வு செய்து `Run workflow` அழுத்தவும்.
5. Build வெற்றியடைந்ததும் workflow run-இன் Artifacts பகுதியில் `RD-Player-debug-apk` download செய்யவும்.
6. ZIP-ஐ extract செய்து `app-debug.apk`-ஐ Android phone-க்கு மாற்றி install செய்யலாம்.

## முக்கியமான வரம்பு
- இது debug APK; Play Store release அல்ல.
- YouTube playback அதிகாரப்பூர்வ embedded player வழியாக மட்டுமே. YouTube screen-off/background playback அல்லது audio extraction இல்லை.
- இந்த package-இல் local audio-க்கான முழுமையான persistent background service இன்னும் சேர்க்கப்படவில்லை; அது அடுத்த implementation/build milestone.
- GitHub cloud build-க்கும் Android Gradle Plugin / dependency repositories கிடைக்க வேண்டும்; build failure ஏற்பட்டால் Actions log-ஐ வைத்து திருத்த வேண்டும்.
