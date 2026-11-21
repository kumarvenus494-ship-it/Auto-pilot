package com.venusa.geminiautopilot

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoPilotScreen() }
    }
}

@Composable
private fun AutoPilotScreen() {
    val context = LocalContext.current
    var waitSeconds by remember { mutableStateOf("8") }
    var monitoring by remember { mutableStateOf(GeminiAccessibilityService.isMonitoring) }
    val enabled = remember {
        mutableStateOf(isAccessibilityEnabled(context, GeminiAccessibilityService::class.java))
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Gemini AutoPilot", style = MaterialTheme.typography.headlineMedium)
                Text("Automatic Next sender for sequential Gemini clip generation.")
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How to use", style = MaterialTheme.typography.titleMedium)
                        Text("1. Gemini me apna master prompt manually bhejo: har baar 1-minute clip banao aur mere Next kehne par agla clip banao.")
                        Text("2. Yahan Accessibility enable karo.")
                        Text("3. Gemini app kholkar use foreground me rakho, phir Start Monitoring dabao.")
                        Text("4. Gemini response complete hone par app kuch seconds rukkar Next bhejne ki koshish karega.")
                    }
                }
                OutlinedTextField(
                    value = waitSeconds,
                    onValueChange = { value -> waitSeconds = value.filter(Char::isDigit).take(2) },
                    label = { Text("Wait after response (seconds)") },
                    supportingText = { Text("Default 8 seconds. Increase if Gemini pauses between updates.") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (enabled.value) "Open Accessibility Settings" else "Enable Accessibility") }

                Button(
                    onClick = {
                        val seconds = waitSeconds.toLongOrNull()?.coerceIn(2, 60) ?: 8L
                        GeminiAccessibilityService.completionWaitMs = seconds * 1000
                        GeminiAccessibilityService.isMonitoring = true
                        monitoring = true
                    },
                    enabled = !monitoring,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start Monitoring") }

                OutlinedButton(
                    onClick = {
                        GeminiAccessibilityService.isMonitoring = false
                        monitoring = false
                    },
                    enabled = monitoring,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Stop") }

                Text(if (monitoring) "Status: Monitoring enabled" else "Status: Stopped")
                Text(
                    "Important: Experimental prototype. Gemini ka interface badalne par automation fail ho sakta hai. Phone unlocked rakho. Final clip se pehle Stop dabao; app ko nahi pata hota ki kaunsa clip aakhri hai.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun isAccessibilityEnabled(context: android.content.Context, service: Class<*>): Boolean {
    val expected = ComponentName(context, service)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    return splitter.any { ComponentName.unflattenFromString(it) == expected }
}
