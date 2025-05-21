package com.example.myapp.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapp.api.AIMessage
import com.example.myapp.api.AIService
import com.example.myapp.data.AppRepository
import kotlinx.coroutines.launch

// 聊天消息的数据类
data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun AIScreen() {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val aiService = remember { AIService() }
    val coroutineScope = rememberCoroutineScope()
    
    // 从仓库获取聊天历史和设置
    val chatHistory by repository.chatHistory.collectAsState(initial = emptyList())
    val apiUrl by repository.apiUrl.collectAsState(initial = "https://api.deepseek.com")
    val apiKey by repository.apiKey.collectAsState(initial = "sk-8e1a2e34388048cd94e168264cfd138d")
    val model by repository.aiModel.collectAsState(initial = "deepseek-chat")
    
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    
    // 首次显示屏幕时加载聊天历史
    LaunchedEffect(chatHistory) {
        if (messages.isEmpty() && chatHistory.isNotEmpty()) {
            messages = chatHistory
        }
    }
    
    // 消息变化时滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 聊天消息
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 显示加载指示器
                if (isLoading) {
                    item {
                        LoadingBubble()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            // 输入框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("向AI提问...") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            // 添加用户消息
                            val userMessage = ChatMessage(inputText, true)
                            val updatedMessages = messages + userMessage
                            messages = updatedMessages
                            
                            // 保存聊天历史
                            coroutineScope.launch {
                                repository.saveChatHistory(updatedMessages)
                            }
                            
                            // 获取用户输入并清空输入框
                            val userInput = inputText
                            inputText = ""
                            
                            // 标记为正在加载
                            isLoading = true
                            
                            // 调用AI API
                            coroutineScope.launch {
                                // 为API创建消息列表
                                val aiMessages = mutableListOf<AIMessage>()
                                
                                // 添加系统消息
                                aiMessages.add(AIMessage(
                                    role = AIMessage.ROLE_SYSTEM,
                                    content = "你是一个有帮助的助手。"
                                ))
                                
                                // 添加对话历史
                                updatedMessages.forEach { message ->
                                    val role = if (message.isFromUser) AIMessage.ROLE_USER else AIMessage.ROLE_ASSISTANT
                                    aiMessages.add(AIMessage(role, message.text))
                                }
                                
                                try {
                                    // 调用API
                                    val response = aiService.sendChatRequest(
                                        apiUrl = apiUrl,
                                        apiKey = apiKey,
                                        model = model,
                                        messages = aiMessages
                                    )
                                    
                                    // 添加AI响应
                                    val aiMessage = ChatMessage(response, false)
                                    val finalMessages = updatedMessages + aiMessage
                                    messages = finalMessages
                                    
                                    // 保存更新的聊天历史
                                    repository.saveChatHistory(finalMessages)
                                } catch (e: Exception) {
                                    // 处理错误
                                    val errorMessage = ChatMessage(
                                        "错误: ${e.message ?: "未知错误"}。请检查您的API设置。",
                                        false
                                    )
                                    messages = updatedMessages + errorMessage
                                    repository.saveChatHistory(messages)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun LoadingBubble() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI正在思考...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
} 