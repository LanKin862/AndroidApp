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
 * 处理使用DeepSeek API格式的AI API请求的服务
 */
class AIService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 向DeepSeek API发送聊天请求
     * 
     * @param apiUrl API的基础URL（默认：https://api.deepseek.com）
     * @param apiKey 用于认证的API密钥
     * @param model 使用的模型（例如，"deepseek-chat"）
     * @param messages 聊天消息
     * @return AI响应或错误消息
     */
    suspend fun sendChatRequest(
        apiUrl: String,
        apiKey: String,
        model: String,
        messages: List<AIMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            // 构建请求JSON
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
            
            // 创建HTTP请求
            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url("$apiUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            // 执行请求
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "错误: ${response.code} - ${response.message}"
                }
                
                // 解析响应
                val responseBody = response.body?.string() ?: return@withContext "错误: 空响应"
                val jsonResponse = JSONObject(responseBody)
                
                if (jsonResponse.has("error")) {
                    val error = jsonResponse.getJSONObject("error")
                    return@withContext "API错误: ${error.optString("message", "未知错误")}"
                }
                
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.getJSONObject("message")
                    return@withContext message.getString("content")
                } else {
                    return@withContext "错误: 没有响应内容"
                }
            }
        } catch (e: IOException) {
            return@withContext "网络错误: ${e.message}"
        } catch (e: Exception) {
            return@withContext "错误: ${e.message}"
        }
    }
}

/**
 * 表示DeepSeek API格式的聊天消息的数据类
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