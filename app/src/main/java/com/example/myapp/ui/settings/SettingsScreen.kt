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

// Extension function for DataStore
val Context.dataStore by preferencesDataStore(name = "settings")

// Preference Keys
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
    
    // Get settings from repository
    val savedApiUrl by repository.apiUrl.collectAsState(initial = "")
    val savedApiKey by repository.apiKey.collectAsState(initial = "")
    val savedModel by repository.aiModel.collectAsState(initial = "")
    
    var apiUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var isSaved by remember { mutableStateOf(false) }
    
    // Available models
    val models = listOf(
        "deepseek-chat",
        "deepseek-reasoner"
    )
    
    // Dropdown state
    var expanded by remember { mutableStateOf(false) }
    
    // Load saved settings
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
                text = "AI Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { 
                    apiUrl = it
                    isSaved = false
                },
                label = { Text("AI API URL") },
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
                label = { Text("AI API Key") },
                placeholder = { Text("Enter your DeepSeek API key") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Model dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("AI Model") },
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
                    // Save settings using repository
                    coroutineScope.launch {
                        repository.saveAISettings(apiUrl, apiKey, selectedModel)
                        isSaved = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
            
            if (isSaved) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Settings saved successfully!",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Instructions
            Text(
                text = "API Configuration Help",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            
            Text(
                text = "This app uses the DeepSeek API format for AI chat functionality. Set your API URL to 'https://api.deepseek.com' and enter your API key from the DeepSeek platform. Select the model you'd like to use from the dropdown menu.",
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