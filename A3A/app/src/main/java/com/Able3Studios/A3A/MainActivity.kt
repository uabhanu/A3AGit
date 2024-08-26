package com.Able3Studios.A3A

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.Able3Studios.A3A.ui.theme.A3ATheme
import kotlinx.coroutines.delay
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A3ATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OTPScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun OTPScreen(modifier: Modifier = Modifier) {
    // State for the OTP and the countdown timer
    var otp by remember { mutableStateOf(generateOTP()) }
    var countdown by remember { mutableStateOf(10) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Start a coroutine that updates the OTP and countdown timer
    LaunchedEffect(Unit) {
        while (true) {
            if (countdown > 0) {
                delay(1000L)
                countdown--
            } else {
                otp = generateOTP()
                countdown = 10
            }
        }
    }

    // Layout for the OTP screen
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Able 3 Authenticator", modifier = Modifier.padding(bottom = 16.dp))
        Text(text = "OTP: ${otp.chunked(2).joinToString(" ")}", modifier = Modifier.padding(bottom = 16.dp))
        Text(text = "Auth code expires in: $countdown seconds", modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = {
            copyToClipboard(context, otp)
        }) {
            Text("Copy Authcode")
        }
    }
}

// Function to generate a 6-digit OTP
fun generateOTP(): String {
    return (Random().nextInt(900000) + 100000).toString()
}

// Function to copy the OTP to the clipboard
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OTP", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "OTP copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Preview(showBackground = true)
@Composable
fun OTPScreenPreview() {
    A3ATheme {
        OTPScreen()
    }
}