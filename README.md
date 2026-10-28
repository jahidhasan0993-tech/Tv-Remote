# TV Remote (Free) — Kotlin / Android

কোনো বিজ্ঞাপন নেই, কোনো থার্ড-পার্টি SDK নেই। Android/Google TV-কে Wi-Fi এর
মাধ্যমে কন্ট্রোল করে Google-এর নিজস্ব **ADB (Android Debug Bridge)**
প্রোটোকল দিয়ে — ঠিক যেভাবে PC থেকে `adb shell input keyevent ...` কাজ করে।

## এটা কীভাবে কাজ করে

- `adb/AdbProtocol.kt` — ADB-র ওয়্যার প্রোটোকল (CNXN/AUTH/OPEN/OKAY/WRTE/CLSE মেসেজ ফ্রেমিং)
- `adb/AdbCrypto.kt` — অ্যাপের নিজস্ব RSA কী-পেয়ার তৈরি/সংরক্ষণ ও সাইনিং (ঠিক `adb`-র মতো)
- `adb/AdbConnection.kt` — TCP সকেট, হ্যান্ডশেক, এবং প্রতিটি বাটন প্রেসে নতুন shell স্ট্রিম ওপেন করে কমান্ড পাঠানো
- `MainActivity.kt` — UI (IP ফিল্ড + D-pad + ভল্যুম/হোম/ব্যাক/পাওয়ার বাটন)

## TV-তে যা অন করতে হবে (একবারই)

1. TV-তে: **Settings → Device Preferences → About → Build** এ ৭ বার ক্লিক করে Developer mode অন করো
2. **Settings → Device Preferences → Developer options → Network debugging** অন করো
3. TV-র IP address নোট করো (**Settings → Network**)
4. এই অ্যাপে সেই IP দিয়ে "সংযুক্ত করো" চাপো
5. প্রথমবার TV স্ক্রিনে "Allow debugging?" টাইপ একটা popup আসবে — TV-র নিজস্ব রিমোট দিয়ে Allow করো (এটা Google-এর security feature, বাইপাস করা হয়নি/যায় না)

## বিল্ড করতে

Android Studio-তে এই ফোল্ডার খোলো (`File → Open`), Gradle sync শেষ হলে
Run চাপো, অথবা টার্মিনালে:

```
./gradlew assembleDebug
```

APK পাবে: `app/build/outputs/apk/debug/app-debug.apk`

## সীমাবদ্ধতা / পরের ধাপ

- বর্তমানে প্রতিটি বাটন-প্রেস একটা নতুন `shell:` স্ট্রিম খোলে — কাজ করে, কিন্তু
  খুব দ্রুত বাটন চাপলে সামান্য ল্যাগ হতে পারে।
- TV-তে ডিফল্টরূপে ADB **Wi-Fi debugging বন্ধ** থাকে — ব্যবহারকারীকে নিজে অন করতে হবে,
  এটা স্বাভাবিক Android আচরণ।
- আইকন/লঞ্চার আর্টওয়ার্ক যোগ করা হয়নি (ডিফল্ট Android আইকন ব্যবহৃত হবে)।
