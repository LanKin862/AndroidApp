# AI Music Player

A music player app for Android with AI chat integration. Built with Jetpack Compose and Kotlin.

## Features

### Music Player
- Browse and play local music from your device
- Import additional music files from any location
- Now Playing bar with current song info and play/pause control
- Interactive Now Playing bar that:
  - Shows currently playing song information
  - Updates automatically when songs change
  - Provides quick play/pause control
  - Can be tapped to navigate to detailed music view
- Detailed music playback screen with:
  - Album artwork display
  - Song and artist information
  - Interactive progress slider for seeking
  - Playback controls (play/pause, previous/next)
  - Sequential or random playback mode selection
  - Lyrics display
- Full playlist support:
  - Navigate between songs with previous/next buttons
  - Automatic progression to next song when current one finishes
  - Toggle between sequential and random playback modes
  - Detail screen automatically updates when changing songs

### AI Chat
- Chat with an AI assistant powered by DeepSeek API
- Configure custom AI API settings:
  - API URL (default: https://api.deepseek.com)
  - API Key
  - Model selection (deepseek-chat, deepseek-coder, etc.)
- User-friendly chat interface with typing indicators
- Error handling for API connectivity issues
- Automatic saving of chat history

### Settings
- Configure AI API endpoints and authentication
- Select AI model from available DeepSeek models
- Settings are persisted between sessions

## Technical Details

This application is built with:
- Kotlin programming language
- Jetpack Compose for the UI
- Android MediaPlayer for audio playback
- DataStore for persistent storage
- Navigation Component for screen navigation
- OkHttp for network requests to AI API

## Architecture

The app follows a modular architecture with:
- UI components in `ui` package separated by feature
- Data persistence in `data` package 
- Music playback functionality in `music` package
- API services in `api` package

## AI Integration

The app integrates with DeepSeek's AI API using the following format:
```
POST /v1/chat/completions
Authorization: Bearer <DeepSeek API Key>
Content-Type: application/json

{
  "model": "deepseek-chat",
  "messages": [
    { "role": "system", "content": "You are a helpful assistant." },
    { "role": "user", "content": "Hello, AI!" }
  ]
}
```

## Requirements

- Android 7.0 (API Level 24) or higher
- Storage permission for accessing local music
- Internet permission for AI functionality

## Setup

1. Clone the repository
2. Open in Android Studio
3. Build and run on device or emulator
4. In the Settings screen, configure your DeepSeek API key

## Permissions

The app requires the following permissions:
- `READ_EXTERNAL_STORAGE` - For accessing music files
- `INTERNET` - For communicating with AI services 