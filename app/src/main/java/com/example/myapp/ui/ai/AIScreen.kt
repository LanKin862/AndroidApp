package com.example.myapp.ui.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapp.api.AIMessage
import com.example.myapp.api.AIService
import com.example.myapp.data.AppRepository
import dev.jeziellago.compose.markdowntext.MarkdownText
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
    val clipboardManager = LocalClipboardManager.current
    
    // 从仓库获取聊天历史和设置
    val chatHistory by repository.chatHistory.collectAsState(initial = emptyList())
    val apiUrl by repository.apiUrl.collectAsState(initial = "https://api.deepseek.com")
    val apiKey by repository.apiKey.collectAsState(initial = "sk-8e1a2e34388048cd94e168264cfd138d")
    val model by repository.aiModel.collectAsState(initial = "deepseek-chat")
    
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentStreamingMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // 首次显示屏幕时加载聊天历史
    LaunchedEffect(chatHistory) {
        if (messages.isEmpty() && chatHistory.isNotEmpty()) {
            messages = chatHistory
        }
    }
    
    // 消息变化时滚动到底部
    LaunchedEffect(messages.size, currentStreamingMessage) {
        if (messages.isNotEmpty() || currentStreamingMessage.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                state = listState
            ) {
                // 显示已保存的消息
                items(messages) { message ->
                    ChatBubble(
                        message = message,
                        onCopy = { 
                            clipboardManager.setText(AnnotatedString(message.text))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 显示正在流式传输的消息
                if (currentStreamingMessage.isNotEmpty()) {
                    item {
                        ChatBubble(
                            message = ChatMessage(currentStreamingMessage, false),
                            onCopy = { 
                                clipboardManager.setText(AnnotatedString(currentStreamingMessage))
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                // 显示加载指示器 (当没有流式消息但正在加载时)
                else if (isLoading) {
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
                            currentStreamingMessage = ""
                            
                            // 创建API消息列表
                            val aiMessages = mutableListOf<AIMessage>()
                            
                            // 添加系统消息
                            aiMessages.add(
                                AIMessage(
                                    role = AIMessage.ROLE_SYSTEM,
                                    content = """
        角色设定
        音乐推荐专家 - MelodyBot
        
        核心能力
        1. 多维度音乐推荐：
           根据情绪/场景推荐（如工作专注、运动健身、放松休息）
           根据音乐流派推荐（流行、摇滚、古典、电子等）
           根据年代/文化推荐（80年代金曲、K-pop最新榜单等）
        
        2. 专业特性：
           了解歌曲的BPM、调式、音乐理论等专业元素
           能分析歌词情感和主题
           熟悉各大音乐平台（Spotify、Apple Music等）的播放列表
        
        交互规则
        使用Markdown格式回复
        推荐时按此结构：
          推荐理由
          [解释为什么适合当前请求]
          
          推荐曲目
          1. [歌曲名] - [艺术家] ([年份])
             ▶️ [特色说明]  
             🎵 [可选：Spotify/Apple Music链接]
        可主动询问细节：
          "您最近喜欢听什么风格？"
          "这次推荐的场景是？"
        
        初始化问候
        "您好！我是音乐助手MelodyBot，请告诉我您今天的音乐需求~"
        """
                                )
                            )
                            
                            // 添加对话历史
                            updatedMessages.forEach { message ->
                                val role = if (message.isFromUser) AIMessage.ROLE_USER else AIMessage.ROLE_ASSISTANT
                                aiMessages.add(AIMessage(role, message.text))
                            }
                            
                            // 使用流式API
                            aiService.sendStreamingChatRequest(
                                apiUrl = apiUrl,
                                apiKey = apiKey,
                                model = model,
                                messages = aiMessages,
                                callback = object : AIService.StreamingResponseCallback {
                                    override fun onToken(token: String) {
                                        // 为每个新令牌更新当前的流式消息
                                        currentStreamingMessage += token
                                    }
                                    
                                    override fun onComplete(fullResponse: String) {
                                        // 流式传输完成，将完整响应添加到消息列表
                                        val aiMessage = ChatMessage(fullResponse, false)
                                        val finalMessages = updatedMessages + aiMessage
                                        
                                        // 更新UI并清除流式消息
                                        coroutineScope.launch {
                                            messages = finalMessages
                                            currentStreamingMessage = ""
                                            isLoading = false
                                            
                                            // 保存更新的聊天历史
                                            repository.saveChatHistory(finalMessages)
                                        }
                                    }
                                    
                                    override fun onError(error: String) {
                                        // 处理错误
                                        coroutineScope.launch {
                                            val errorMessage = ChatMessage(
                                                "错误: $error。请检查您的API设置。",
                                                false
                                            )
                                            messages = updatedMessages + errorMessage
                                            currentStreamingMessage = ""
                                            isLoading = false
                                            repository.saveChatHistory(messages)
                                        }
                                    }
                                }
                            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(message: ChatMessage, onCopy: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        var showContextMenu by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .combinedClickable(
                    onClick = { /* 单击不做任何事情 */ },
                    onLongClick = { showContextMenu = true }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                if (message.isFromUser) {
                    // 用户消息显示为普通文本
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // AI消息使用Markdown渲染
                    MarkdownText(
                        markdown = message.text,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        linkColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
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