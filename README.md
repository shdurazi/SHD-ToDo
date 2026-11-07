<p align="center">
  <img src="docs/icon_rounded.png" width="128" alt="ToDo icon">
</p>

<h1 align="center">ToDo</h1>

<p align="center">
  A calm daily planner for Android: your to-dos on a dark card, your day as time blocks on a light timeline,
  a focus timer, reminders, and widgets for the home screen and the lock screen.
</p>

<p align="center">
  <a href="#english">English</a> · <a href="#العربية">العربية</a>
</p>

---

## English

### What it does

- **To-dos in projects.** Every project has its own colour and icon. Type into *New To Do* and the quick-add parser
  understands things like `call Sara tomorrow 5pm`, `!!` for priority and project names. A *Nice to do* section keeps
  the "someday" items out of the way, and finished items collect in a *Completed* list.
- **A timeline for the day.** Time blocks with a colour, an icon, a length and an optional alert. Swipe the date bar to
  change day, drag it to give the timeline more or less room, long-press an empty slot to add a block.
- **Drag and drop.** Hold a to-do and drop it on a block, on an empty part of the timeline (a new block is created)
  or on the bin to delete it.
- **Focus mode.** Tap a block to start a countdown with its to-dos next to it. The countdown also lives in a
  notification (with a live chronometer and a *Stop* action).
- **Notifications.** To-do reminders (with *Done* and *Snooze*), a heads-up a few minutes before every block, a daily
  summary, and a note when a focus session ends. An optional ongoing notification shows what is next.
- **Widgets.** *Tasks* (tick items off straight from the widget), *Timeline* and *Next up*. They are declared for the
  home screen **and** the lock screen (`home_screen|keyguard`); whether your phone offers lock-screen widgets depends
  on its Android version and manufacturer.
- **Shortcuts.** A *New To Do* launcher shortcut and a Quick Settings tile.
- **Private by design.** Everything is stored in one JSON file on the phone. No account, no ads, no tracking, no network
  permission. Export or restore a backup from Settings.

### Install

Open the latest build on the **Releases** page of this repository, download `ToDo-release.apk` on your phone and open
it (Android asks once to allow installs from your browser or file manager).
`ToDo-debug.apk` is the same app without optimisation and is only meant for troubleshooting.

Every push to `main` is built by GitHub Actions (`.github/workflows/android.yml`) and published as a new release.
If a build ever fails, the workflow posts the compiler errors as a comment on the commit.
All builds are signed with the same key and get a higher version number each time, so a newer APK installs over the
older one and keeps your data.

### Build it yourself

Requirements: JDK 17 and the Android SDK (platform 36).

```bash
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

Both builds are signed with the fixed key in `keystore/todo.keystore` (committed on purpose, password `android`) so that
every build installs over the previous one. It is only meant for sideloading this personal app: use your own private
keystore before publishing anywhere else.

### How it is built

Kotlin, Jetpack Compose (without Material components — every control is custom drawn), Jetpack Glance for the
widgets, `AlarmManager` for reminders. The data model, the JSON codec, the quick-add parser and the timeline layout
are plain Kotlin without Android dependencies.

```
app/src/main/java/com/shdurazi/todo
├── core/      data model, JSON, quick-add parser, timeline layout, queries, planner, repository
├── notify/    channels, notifications, alarm scheduling, receivers, Quick Settings tile
├── widget/    Tasks / Timeline / Next up widgets (Glance)
└── ui/        theme, components, home, sheets, focus, settings
```

### Credits

Typeface: [Inter](https://rsms.me/inter/) (SIL Open Font License, see `licenses/`).

---

## العربية

### ما هو التطبيق؟

مخطط يومي هادئ لأندرويد: مهامك على بطاقة داكنة، ويومك كفترات زمنية على جدول فاتح، مع مؤقّت تركيز وتذكيرات وودجت
للشاشة الرئيسية وشاشة القفل.

- **مهام داخل مشاريع** لكل مشروع لون وأيقونة. الإضافة السريعة تفهم عبارات مثل `اتصل بسارة tomorrow 5pm` وعلامة `!!`
  للأولوية وأسماء المشاريع، وقسم «Nice to do» للمهام غير العاجلة وقائمة «Completed» للمنجَز.
- **جدول زمني لليوم** بفترات ملوّنة بأيقونة ومدة وتنبيه اختياري. اسحب شريط التاريخ لتغيير اليوم أو لتكبير الجدول
  وتصغيره، واضغط مطوّلاً على مكان فارغ لإضافة فترة.
- **السحب والإفلات:** أمسك مهمة وأفلتها على فترة، أو على مكان فارغ (تُنشأ فترة جديدة)، أو على سلة الحذف.
- **وضع التركيز:** اضغط على فترة لبدء العدّ التنازلي، ويظهر أيضاً في إشعار مع زر إيقاف.
- **الإشعارات:** تذكير بالمهام (تم / تأجيل)، تنبيه قبل كل فترة، ملخص يومي، وإشعار عند انتهاء التركيز، مع إشعار دائم اختياري
  بما هو قادم.
- **الودجت:** المهام (علّم المهمة منجزة من الودجت نفسه)، الجدول الزمني، و«التالي». معرَّفة للشاشة الرئيسية ولشاشة القفل؛
  وتوفّر ودجت القفل يعتمد على إصدار أندرويد وشركة الجهاز.
- **اختصارات:** اختصار «New To Do» على أيقونة التطبيق وبلاطة في الإعدادات السريعة.
- **خصوصية كاملة:** كل البيانات في ملف واحد على الهاتف، بلا حساب ولا إعلانات ولا تتبّع ولا صلاحية إنترنت. نسخ احتياطي
  واستعادة من الإعدادات.

### التثبيت

افتح صفحة **Releases** في هذا المستودع، نزّل الملف `ToDo-release.apk` على هاتفك وافتحه (سيطلب أندرويد السماح بالتثبيت من
المتصفح أو مدير الملفات مرة واحدة). الملف `ToDo-debug.apk` للتجربة وحل المشاكل فقط.

كل تحديث على الفرع `main` يُبنى تلقائياً عبر GitHub Actions ويُنشر كإصدار جديد. كل النسخ موقّعة بنفس المفتاح، فالنسخة
الأحدث تُثبَّت فوق القديمة وتحتفظ ببياناتك.
