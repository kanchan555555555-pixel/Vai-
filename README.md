# HotCast TV — Offline Hotspot Screen Mirror & TV Cast

ইন্টারনেট ছাড়াই ফোনের **Wi-Fi Hotspot** ব্যবহার করে স্মার্ট টিভিতে **Live Screen Mirroring** এবং ভিডিও, ছবি ও অডিও কাস্ট করার Android অ্যাপ।

## বৈশিষ্ট্যসমূহ (Features)
- **Zero Internet Required:** মোবাইল ডেটা বা ইন্টারনেট ছাড়াই শুধু ফোনের হটস্পট ও টিভির Wi-Fi দিয়ে চলে।
- **Live Screen Mirroring:** Android `MediaProjection` দিয়ে ফোনের পুরো স্ক্রিন টিভির ব্রাউজারে লাইভ মিরর হয়।
- **Universal TV Web Receiver:** যেকোনো Smart TV / Android TV ব্রাউজারে `http://<hotspot-ip>:8899` খুললেই বা QR স্ক্যান করলেই চলে।
- **Smart TV DLNA Cast:** একই নেটওয়ার্কে থাকা DLNA/UPnP টিভি অটো-স্ক্যান করে সরাসরি মিডিয়া প্লে ও রিমোট কন্ট্রোল করে।

## ইন্টারনেট ছাড়া টিভিতে ব্যবহারের নিয়ম
1. ফোনের **Mobile Hotspot** চালু করুন (ইন্টারনেট/মোবাইল ডেটা বন্ধ রাখলেও চলবে)।
2. টিভিকে ফোনের সেই হটস্পটে Wi-Fi দিয়ে কানেক্ট করুন।
3. টিভির ব্রাউজারে অ্যাপে দেখানো লিংকটি (যেমন `http://192.168.43.1:8899`) খুলুন বা QR কোড স্ক্যান করুন।
4. অ্যাপ থেকে **স্ক্রিন মিররিং শুরু করুন** চাপুন অথবা ভিডিও/ছবি/অডিও সিলেক্ট করে কাস্ট করুন।

---

## APK ইন্সটল ও GitHub Auto-Release সেটআপ

### বিকল্প ১: AI Studio থেকে সরাসরি APK ডাউনলোড
AI Studio-এর উপরের মেনু/সেটিংস থেকে সরাসরি **APK / AAB Generate** করে ডাউনলোড ও ইন্সটল করতে পারবেন।

### বিকল্প ২: GitHub-এ Auto Build ও Release চালু করা
নিরাপত্তার কারণে GitHub OAuth অ্যাপ দিয়ে সরাসরি `.github/workflows/` ফাইল পুশ করা যায় না। কোড পুশ করার পর GitHub ওয়েবসাইটে একবার নিচের ফাইলটি যোগ করে নিলেই প্রতিবার অটোমেটিক APK Build ও Release হবে:

1. GitHub রিপোজিটরিতে **Add file → Create new file** চাপুন।
2. ফাইলের নাম দিন: `.github/workflows/release-apk.yml`
3. নিচের কোডটি পেস্ট করে **Commit changes** দিন:

```yaml
name: Build & Release HotCast TV APK

on:
  push:
  workflow_dispatch:
    inputs:
      release_tag:
        description: 'Release tag name (e.g. v1.0.0)'
        required: false
        default: ''

permissions:
  contents: write

jobs:
  build-apk:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v4
        with:
          packages: 'platform-tools'

      - name: Install Android 36.1 SDK & Accept Licenses
        run: |
          SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
          mkdir -p "$SDK_DIR/licenses"
          printf '\n24333f8a63b6825ea9c5514f83c2829b004d1fee' > "$SDK_DIR/licenses/android-sdk-license"
          printf '\nd56f5187479451eabf01fb78af6dfcb131a6481e' >> "$SDK_DIR/licenses/android-sdk-license"
          printf '\n84831b9409646a918e30573bab4c9c91346d8abd' > "$SDK_DIR/licenses/android-sdk-preview-license"
          SDKMGR=$(find "$SDK_DIR" -name sdkmanager -type f 2>/dev/null | head -n 1 || true)
          if [ -n "$SDKMGR" ]; then
            yes | "$SDKMGR" --licenses >/dev/null 2>&1 || true
            "$SDKMGR" "platforms;android-36.1" "build-tools;36.0.0" || "$SDKMGR" "platforms;android-36" "build-tools;36.0.0" || true
          fi

      - name: Ensure valid debug keystore exists
        run: |
          rm -f debug.keystore
          if [ -s debug.keystore.base64 ]; then
            tr -d ' \n\r' < debug.keystore.base64 | base64 -d > debug.keystore || true
          fi
          if ! keytool -list -keystore debug.keystore -storepass android >/dev/null 2>&1; then
            rm -f debug.keystore
            keytool -genkeypair -v -keystore debug.keystore \
              -storepass android -alias androiddebugkey -keypass android \
              -keyalg RSA -keysize 2048 -validity 10000 \
              -dname "CN=Android Debug,O=Android,C=US"
          fi

      - name: Set up Gradle 9.3.1
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.3.1'
          validate-wrappers: false

      - name: Build installable APK
        run: gradle :app:assembleDebug --no-daemon --stacktrace

      - name: Prepare APK artifacts
        run: |
          mkdir -p release-apks
          DEBUG_APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)
          if [ -z "$DEBUG_APK" ]; then
            echo "Error: Debug APK not found!"
            exit 1
          fi
          cp "$DEBUG_APK" release-apks/HotCastTV-installable.apk

      - name: Upload APK workflow artifact
        uses: actions/upload-artifact@v4
        with:
          name: HotCastTV-APK
          path: release-apks/*.apk

      - name: Determine Release Tag
        id: release_meta
        run: |
          if [ "${{ github.event_name }}" = "workflow_dispatch" ] && [ -n "${{ github.event.inputs.release_tag }}" ]; then
            TAG="${{ github.event.inputs.release_tag }}"
            IS_TAG="false"
          elif [[ "${{ github.ref }}" == refs/tags/* ]]; then
            TAG="${{ github.ref_name }}"
            IS_TAG="true"
          else
            TAG="v1.0.${{ github.run_number }}"
            IS_TAG="false"
          fi
          echo "tag=$TAG" >> "$GITHUB_OUTPUT"
          echo "is_tag=$IS_TAG" >> "$GITHUB_OUTPUT"

      - name: Publish GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          TAG: ${{ steps.release_meta.outputs.tag }}
          IS_TAG: ${{ steps.release_meta.outputs.is_tag }}
        run: |
          printf '%s\n' \
            "### HotCast TV — Offline Hotspot Screen Mirroring & TV Cast APK" \
            "Download \`HotCastTV-installable.apk\` below and install directly on your Android phone." \
            > release-notes.md

          if gh release view "$TAG" >/dev/null 2>&1; then
            gh release upload "$TAG" release-apks/*.apk --clobber
          elif [ "$IS_TAG" = "true" ]; then
            gh release create "$TAG" release-apks/*.apk \
              --title "HotCast TV $TAG" \
              --notes-file release-notes.md
          else
            gh release create "$TAG" release-apks/*.apk \
              --target "${{ github.sha }}" \
              --title "HotCast TV $TAG" \
              --notes-file release-notes.md
          fi
```

4. এরপর রিপোজিটরির **Settings → Actions → General → Workflow permissions** থেকে **Read and write permissions** চালু করে **Save** দিন।
5. বিল্ড শেষ হলে রিপোজিটরির ডানপাশের **Releases** থেকে **`HotCastTV-installable.apk`** ডাউনলোড করে ফোনে ইন্সটল করুন।
