# VocabVault

VocabVault is a modern, offline-first, client-side Android application designed for learners building their personal English-to-Farsi vocabulary database. The app provides full offline access to your curated dictionary, combined with a highly configurable Spaced Repetition System (SRS) to reinforce retention.

---

## 🚀 Key Features

- **Full Offline Access**: Browse your word lists, track analytics, and complete your scheduled exercises without requiring an active internet connection.
- **Flexible Data Seeding and Manual Entry**: Populate your vocabulary database via a local `.csv` or `.txt` file using a straightforward 3-column structural layout or add words manually one by one and auto-fetch English definition and translation via [MyMemory API](https://mymemory.translated.net/).
- **Review Scopes & Algorithms**: Design study sessions using scope criteria (such as *All previous words* or *Only Troublesome words*) and dynamic retrieval logic (*Least practiced first*, *Random*, or *In order*).
- **Learning & Review Modules**: Separate tracks for initializing new words into your memory bank versus systematically testing older words.
- **Configurable Schedules**: Set exact learning times (e.g., Daily at 10:00 AM) with targeted session goals (e.g., 5 words per day).
- **Local Notifications**: Automated local push reminders fire precisely at scheduled workout times using Android's native scheduling utilities.
- **Mastery Levels**: Words are automatically categorized using sequential practice behavior: **Unlearned**, **Learning**, **Fully learned**, **Troublesome words**.
- **Comprehensive Analytics**: Track total words learned, current retention curves, consistency heatmap, and visual breakdowns of your mastery distribution levels.
- **Backup and Restore**: Easily export your study data and restore somewhere else.

---

## 📂 Project Architecture

The codebase strictly adheres to standard Android recommendations using **MVVM** / **Clean Architectural Layers**:
- `com.example.data`: Secure Room SQLite Database configuration, DAO interfaces, and Word/Configuration models.
- `com.example.ui.viewmodel`: Central reactive `VocabViewModel` driving all asynchronous state logic, history, and configuration states.
- `com.example.ui.screens`: Modular Jetpack Compose layout engines, including `DashboardScreen` (with settings), `DictionaryScreen` (with word search and filters), and `ExerciseScreen` (dynamic card matching).

---

## 🔨 Tech Stack & Dependencies

- **Framework**: Modern Jetpack Compose, Kotlin, Coroutines & Flows
- **Database**: Android Jetpack Room (SQL Persistence via KSP compilation)
- **Serialization**: Moshi Kotlin & Converters
- **Dependency Ingestion**: Constructor Injection
- **Automation CI/CD**: GitHub Actions

---

## 📦 Setting Up Locally

1. Clone this repository to your local machine:
   ```bash
   git clone <repository-url>
   ```
2. Open the project inside **Android Studio**.
3. Let Gradle complete downloading dependencies and indexing.
4. Execute/run on a connected Android Device or Emulator.

To build the package directly via command-line:
```bash
./gradlew assembleDebug
```

---

## 🤖 Continuous Deployment (CD)

The project includes an automated GitHub Actions release cycle configured in `.github/workflows/release.yml`:
- **Trigger**: When you push a tag starting with `v` (e.g. `v1.0.0`).
- **Build Outcome**: Autocreated public releases with attached **VocabVault-${version-tag}.apk** installers. Ensure standard Android device installs can be downloaded and tested immediately!

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
