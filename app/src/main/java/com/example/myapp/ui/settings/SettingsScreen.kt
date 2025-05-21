package com.example.myapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.example.myapp.data.AppRepository
import kotlinx.coroutines.launch
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences

// DataStore扩展函数
val Context.dataStore by preferencesDataStore(name = "settings")

// 偏好设置键
object PreferenceKeys {
    val AI_API_URL = stringPreferencesKey("ai_api_url")
    val AI_API_KEY = stringPreferencesKey("ai_api_key")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 从仓库获取设置
    val savedApiUrl by repository.apiUrl.collectAsState(initial = "")
    val savedApiKey by repository.apiKey.collectAsState(initial = "")
    val savedModel by repository.aiModel.collectAsState(initial = "")
    
    var apiUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var isSaved by remember { mutableStateOf(false) }
    
    // 可用模型
    val models = listOf(
        "deepseek-chat",
        "deepseek-reasoner"
    )
    
    // 下拉菜单状态
    var expanded by remember { mutableStateOf(false) }
    
    // 加载已保存的设置
    LaunchedEffect(savedApiUrl, savedApiKey, savedModel) {
        apiUrl = savedApiUrl
        apiKey = savedApiKey
        selectedModel = savedModel
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { 
                    apiUrl = it
                    isSaved = false
                },
                label = { Text("AI API地址") },
                placeholder = { Text("https://api.deepseek.com") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { 
                    apiKey = it
                    isSaved = false
                },
                label = { Text("AI API密钥") },
                placeholder = { Text("输入您的DeepSeek API密钥") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 模型下拉菜单
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("AI模型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                selectedModel = model
                                expanded = false
                                isSaved = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    // 使用仓库保存设置
                    coroutineScope.launch {
                        repository.saveAISettings(apiUrl, apiKey, selectedModel)
                        isSaved = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }
            
            if (isSaved) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "设置保存成功！",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 指导说明
            Text(
                text = "API配置帮助",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            
            Text(
                text = "此应用使用DeepSeek API格式实现AI聊天功能。将您的API地址设置为'https://api.deepseek.com'并输入您从DeepSeek平台获取的API密钥。从下拉菜单中选择您想使用的模型。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun saveSettings(context: Context, apiUrl: String, apiKey: String) {
    kotlinx.coroutines.runBlocking {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AI_API_URL] = apiUrl
            preferences[PreferenceKeys.AI_API_KEY] = apiKey
        }
    }
} 