# Palmprint Recognition Android App

This project is an Android application designed for palmprint recognition. The app captures palm images, processes them, and uses a server to register and authenticate users based on their palmprints.

## Features

- Capture left and right hand palm images
- Register and authenticate users using palmprint images
- Display real-time results
- Interface with a backend server for image processing and storage

## Requirements

- Android Studio 4.0 or higher
- Gradle 5.6.4 or higher
- Android SDK 30 or higher
- Minimum Android API level 24

## Installation

1. **Clone the repository:**

   ```
   git clone https://github.com/IoNaNO/palm-android.git
   cd palm-android
   ```

2. **Open in Android Studio:**

   - Open Android Studio.
   - Select "Open an existing project" and choose the cloned repository folder.

3. **Set up the backend server:**

   - Ensure the Flask server is running and accessible.
   - Update the `BASE_URL` in `NetworkManager.kt` to point to your server endpoint.

4. **Build and Run:**

   - Connect an Android device or start an emulator.
   - Click on "Run" in Android Studio.

## Usage

1. **Capture Palm Images:**
   - Open the app.
   - Capture images of your left and right palm using the designated buttons.
2. **Register:**
   - Enter your username.
   - Ensure both left and right palm images are captured.
   - Click the "Register" button to send images to the server.
3. **Authenticate:**
   - After registration, use the app to authenticate by capturing and sending palm images to the server.

## Code Structure

- `MainActivity.kt`: The main activity of the app.
- `RegisterFragment.kt`: Handles user registration and image capture.
- `NetworkManager.kt`: Manages network operations, including sending images to the server.
- `PalmPrintRegistrationData.kt`: Data class for storing palmprint images.
- `res/layout`: Contains the XML layout files for UI design.