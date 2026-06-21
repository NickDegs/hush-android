# Hush — Android

iOS Hush'ın Android karşılığı. **Kotlin + Jetpack Compose + Material 3** ile yazıldı (Android'in en performanslı native stack'i).

## Özellikler (1.0)

- Telefon + SMS auth (Twilio Verify üzerinden, iOS ile aynı backend)
- Apple App Review fallback bypass (Play Console için de aynı mantık)
- Matrix REST client (Retrofit + Kotlinx Serialization)
- Liquid Glass tarzı zemin (animated gradient blobs + cam katmanlar)
- App Links: `https://nickdegs.com/hush/chat?token=...`
- Custom scheme: `hush://chat?token=...`
- Adaptive icon + Material You uyumlu
- 12 dil (TR + EN diğerleri eklenecek)

## Yapı

```
hush-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml         # tüm dependency versiyonları
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/nickdegs/hush/
│   │   │   ├── HushApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── core/
│   │   │   │   ├── auth/    PhoneAuthApi, Network
│   │   │   │   ├── matrix/  MatrixClient (TODO)
│   │   │   │   ├── store/   AppViewModel
│   │   │   │   ├── push/    FCM (google-services.json gelince)
│   │   │   │   └── billing/ Google Play Billing (TODO)
│   │   │   └── ui/
│   │   │       ├── theme/   Theme, Type
│   │   │       ├── components/ LiquidBackground, Glass
│   │   │       ├── auth/    LoginScreen, PhoneSignupScreen, OtpVerifyScreen
│   │   │       └── home/    HomeScreen (Tab + RoomList)
│   │   └── res/
│   │       ├── values/strings.xml (en)
│   │       ├── values-tr/strings.xml (tr)
│   │       └── ...
└── .github/workflows/build-android.yml
```

## CI/CD

GitHub Actions her push'ta:
1. AAB derler (release)
2. `ANDROID_KEYSTORE_BASE64` secret varsa imzalar
3. `PLAY_SERVICE_ACCOUNT_JSON` secret varsa Google Play Internal Testing'e yükler

### Gerekli GitHub Secrets

| Secret | Açıklama |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 keystore.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore şifresi |
| `ANDROID_KEY_ALIAS` | Anahtar takma adı |
| `ANDROID_KEY_PASSWORD` | Anahtar şifresi |
| `GOOGLE_SERVICES_JSON_BASE64` | `base64 -w0 google-services.json` |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Cloud SA JSON içeriği (raw) |

## Yerel build

```bash
./gradlew assembleDebug              # debug APK
./gradlew bundleRelease              # release AAB (imzalı keystore varsa)
./gradlew installDebug               # bağlı cihaza yükle
```

## Backend

Aynı `https://nickdegs.com/api/phone/*` endpoint'lerini kullanır (iOS ile paylaşımlı).

## Submit (Google Play Console)

1. Play Console → Hush App oluştur
2. Package name: `com.nickdegs.hush`
3. App Signing: "Use Play App Signing" seç
4. Upload key'i Console'dan al, GitHub Secret olarak yapıştır
5. Service Account oluştur (Google Cloud → IAM → Service Accounts) → JSON key indir → `PLAY_SERVICE_ACCOUNT_JSON` secret
6. `workflow_dispatch` ile internal track'e ilk yükleme

## Sonraki turlar

- [ ] Google Play Billing (yıllık $29.99 Hush Pro)
- [ ] FCM push (`google-services.json` gelince aktif olur)
- [ ] Matrix Sync (long-poll)
- [ ] Spaces (Topluluk), Threads, Reactions, Polls, Search
- [ ] WebRTC Sesli/Görüntülü çağrı
- [ ] E2EE (matrix-rust-sdk-android binding)
- [ ] Ekran görüntüleri 12 dil × 3 boyut
