package com.example.myapp.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for handling AI API requests using the DeepSeek API format
 */
class AIService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Send a chat request to the DeepSeek API
     * 
     * @param apiUrl The base URL of the API (default: https://api.deepseek.com)
     * @param apiKey The API key for authentication
     * @param model The model to use (e.g., "deepseek-chat")
     * @param messages The chat messages
     * @return The AI response or an error message
     */
    suspend fun sendChatRequest(
        apiUrl: String,
        apiKey: String,
        model: String,
        messages: List<AIMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            // Build the request JSON
            val jsonMessages = JSONArray()
            messages.forEach { message ->
                val jsonMessage = JSONObject()
                jsonMessage.put("role", message.role)
                jsonMessage.put("content", message.content)
                jsonMessages.put(jsonMessage)
            }
            
            val requestJson = JSONObject()
            requestJson.put("model", model)
            requestJson.put("messages", jsonMessages)
            
            // Create the HTTP request
            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url("$apiUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            // Execute the request
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Error: ${response.code} - ${response.message}"
                }
                
                // Parse the response
                val responseBody = response.body?.string() ?: return@withContext "Error: Empty response"
                val jsonResponse = JSONObject(responseBody)
                
                if (jsonResponse.has("error")) {
                    val error = jsonResponse.getJSONObject("error")
                    return@withContext "API Error: ${error.optString("message", "Unknown error")}"
                }
                
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.getJSONObject("message")
                    return@withContext message.getString("content")
                } else {
                    return@withContext "Error: No response content"
                }
            }
        } catch (e: IOException) {
            return@withContext "Network error: ${e.message}"
        } catch (e: Exception) {
            return@withContext "Error: ${e.message}"
        }
    }
}

/**
 * Data class representing a chat message in the DeepSeek API format
 */
data class AIMessage(
    val role: String,
    val content: String
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
} 