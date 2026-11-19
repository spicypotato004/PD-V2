# Prima Draw Plus V2

The drawing app (`index.html`) plus everything GitHub needs to turn it into an Android app (APK). GitHub builds the APK for you, so you don't need a computer. It needs Android 8 or newer.

## Make the APK (first time)

1. On github.com, tap **+** → **New repository**. Give it a name such as `prima-draw-plus-v2`. **Private** is best (see *Your signing key* below). Tap **Create repository**.
2. Tap **uploading an existing file** (or **Add file → Upload files**). Pick **all** the files from this folder and tap **Commit changes**.
3. Add the build steps. GitHub only runs them from one special place:
   - Open the **Actions** tab and tap **set up a workflow yourself**. Or use **Add file → Create new file** and type the name `.github/workflows/build-apk.yml`.
   - Open `build-apk.yml` from this folder, copy everything in it, and paste it in. Then tap **Commit changes**.
   - If you uploaded from a computer and the `.github` folder came along, this step is already done.
4. Open the **Actions** tab. **Build APK** starts by itself and takes about 3 to 5 minutes. If GitHub asks, allow Actions to run.
5. When the build has a green tick, open the repository's **Releases** page. It's on the right of the Code tab, or at `github.com/YOUR-NAME/REPO-NAME/releases`. Tap **PrimaDrawPlusV2.apk** to download it.
6. Open the downloaded file and tap **Install**. The first time, Android asks you to allow installs from your browser. Allow it, go back and tap **Install** again.

The first time you open the app, it asks for your password. After that it remembers it.

## Update the app

Upload the new `index.html` with **Add file → Upload files**; it replaces the old one. GitHub builds a new APK automatically. Download it from **Releases** again and install it over the old app. Your drawings and settings stay.

## In the app

- **Save to tablet**:
  - Pictures go to `Pictures/Prima Draw`, so they show up in the Gallery.
  - Timelapse videos go to `Movies/Prima Draw`.
  - Project, PSD and brush files go to `Download/Prima Draw`.
- **Share…** opens Android's share menu (Drive, messages and so on).
- **Back button**: it closes the open panel first, and from the canvas it goes to the gallery. In the gallery, press it twice to close the app.
- Drawings in the app are kept separately from the browser version. To move a drawing, save it as a **Project file (.prima)** in one, then open it in the other.
- **Uninstalling the app deletes the drawings inside it.** Save project files first if you ever need to uninstall.

## Your signing key (`app.keystore`)

Android only updates an app when the new APK is signed with the same key as the old one.

- Keep `app.keystore` in the repository and never replace it. If the key changes, you have to uninstall the app (losing its drawings) before the new one will install.
- Anyone who can see the repository can copy this key. That's why a private repository is safest.
- Advanced: you can keep the key out of the repository instead. Save the base64 text of this same `app.keystore` as a repository secret named `KEYSTORE_BASE64`, and its password (`primadrawv2`) as `KEYSTORE_PASSWORD`. Then delete `app.keystore`.

## Use it as a website too (optional)

Go to **Settings → Pages**, choose branch **main** and folder **/ (root)**, then tap **Save**. The app opens at `https://YOUR-NAME.github.io/REPO-NAME/` and works offline after the first visit. Free GitHub accounts can only do this with public repositories.

## What's in this folder

| File | What it is |
| --- | --- |
| `index.html` | The drawing app, locked with your password |
| `manifest.webmanifest`, `sw.js`, `icon-*.png`, `favicon-48.png`, `apple-touch-icon.png` | App name, icons and offline support for the website version |
| `build-apk.yml` | The build steps (goes in `.github/workflows/`) |
| `MainActivity.java`, `AndroidManifest.xml` | The Android app around the drawing app |
| `android-icon-foreground.png`, `android-icon-background.png` | The Android home-screen icon |
| `app.keystore` | The app's signing key (see above) |
