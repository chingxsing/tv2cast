package com.tv2cast.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.tv2cast.app.ui.theme.*

/**
 * Server connection screen — user enters the server IP:port
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConnectScreen(
    onConnect: (String) -> Unit,
    onDiscover: () -> Unit,
    errorMessage: String? = null
) {
    var serverUrl by remember { mutableStateOf("http://192.168.1.") }
    val focusRequester = remember { FocusRequester() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1040),
                        BgPrimary
                    ),
                    radius = 800f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(32.dp)
        ) {
            // Logo
            Text(
                text = "🎬",
                fontSize = 72.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Title
            Text(
                text = "Tv2Cast",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Subtitle
            Text(
                text = "Connect to your video server",
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 40.dp)
            )
            
            // Server URL input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Server Address",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                BasicTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { onConnect(serverUrl) }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgSecondary, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                        .focusRequester(focusRequester)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Connect button
                androidx.tv.material3.Button(
                    onClick = { onConnect(serverUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Accent,
                        focusedContainerColor = AccentCyan
                    )
                ) {
                    Text(
                        text = "Connect",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-Search button
                androidx.tv.material3.OutlinedButton(
                    onClick = onDiscover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    border = androidx.tv.material3.ButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = 0.5f))
                        )
                    )
                ) {
                    Text(
                        text = "🔍 Auto-Search Server",
                        fontSize = 14.sp,
                        color = AccentCyan
                    )
                }
                
                // Hint
                Text(
                    text = "Enter the IP shown in the server console\ne.g. 192.168.1.100:3456",
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    color = Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(
                            Color(0x20F87171),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Version display at the bottom
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "1.1.0"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = "v$versionName",
            fontSize = 12.sp,
            color = TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
