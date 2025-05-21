package com.example.myapp.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapp.ui.ai.ChatMessage
import com.example.myapp.ui.music.MusicFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// DataStore扩展函数
val Context.dataStore by preferencesDataStore(name = "appSettings")

// 偏好设置键
object PreferenceKeys {
    val AI_API_URL = stringPreferencesKey("ai_api_url")
    val AI_API_KEY = stringPreferencesKey("ai_api_key")
    val AI_MODEL = stringPreferencesKey("ai_model")
    val CHAT_HISTORY = stringPreferencesKey("chat_history")
    val IMPORTED_MUSIC = stringPreferencesKey("imported_music")
}

class AppRepository(private val context: Context) {
    
    // AI设置
    suspend fun saveAISettings(apiUrl: String, apiKey: String, model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AI_API_URL] = apiUrl
            preferences[PreferenceKeys.AI_API_KEY] = apiKey
            preferences[PreferenceKeys.AI_MODEL] = model
        }
    }
    
    val apiUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AI_API_URL] ?: "https://api.deepseek.com"
    }
    
    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AI_API_KEY] ?: "sk-8e1a2e34388048cd94e168264cfd138d"
    }
    
    val aiModel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AI_MODEL] ?: "deepseek-chat"
    }
    
    // 聊天历史
    suspend fun saveChatHistory(chatMessages: List<ChatMessage>) {
        val jsonArray = JSONArray()
        
        chatMessages.forEach { message ->
            val jsonObject = JSONObject()
            jsonObject.put("text", message.text)
            jsonObject.put("isFromUser", message.isFromUser)
            jsonObject.put("timestamp", message.timestamp)
            jsonArray.put(jsonObject)
        }
        
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.CHAT_HISTORY] = jsonArray.toString()
        }
    }
    
    val chatHistory: Flow<List<ChatMessage>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[PreferenceKeys.CHAT_HISTORY] ?: return@map emptyList()
        
        try {
            val jsonArray = JSONArray(jsonString)
            val chatMessages = mutableListOf<ChatMessage>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val text = jsonObject.getString("text")
                val isFromUser = jsonObject.getBoolean("isFromUser")
                val timestamp = jsonObject.getLong("timestamp")
                
                chatMessages.add(ChatMessage(text, isFromUser, timestamp))
            }
            
            chatMessages
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // 导入的音乐
    suspend fun saveImportedMusic(musicFiles: List<MusicFile>) {
        val jsonArray = JSONArray()
        
        musicFiles.forEach { music ->
            val jsonObject = JSONObject()
            jsonObject.put("id", music.id)
            jsonObject.put("title", music.title)
            jsonObject.put("artist", music.artist)
            jsonObject.put("duration", music.duration)
            jsonObject.put("uri", music.uri.toString())
            jsonArray.put(jsonObject)
        }
        
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.IMPORTED_MUSIC] = jsonArray.toString()
        }
    }
    
    val importedMusic: Flow<List<MusicFile>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[PreferenceKeys.IMPORTED_MUSIC] ?: return@map emptyList()
        
        try {
            val jsonArray = JSONArray(jsonString)
            val musicFiles = mutableListOf<MusicFile>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getLong("id")
                val title = jsonObject.getString("title")
                val artist = jsonObject.getString("artist")
                val duration = jsonObject.getLong("duration")
                val uriString = jsonObject.getString("uri")
                
                // 检查文件是否仍然存在
                val uri = Uri.parse(uriString)
                val file = File(uri.path ?: "")
                
                if (file.exists()) {
                    musicFiles.add(MusicFile(id, title, artist, duration, uri))
                }
            }
            
            musicFiles
        } catch (e: Exception) {
            emptyList()
        }
    }
} 