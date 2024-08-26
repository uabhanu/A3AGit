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
import androidx.compose.ui.unit.sp
import com.Able3Studios.A3A.ui.theme.A3ATheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var isButtonEnabled by remember { mutableStateOf(true) }
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

    // Function to handle the button click
    fun handleCopyClick() {
        copyToClipboard(context, otp)
        isButtonEnabled = false
        coroutineScope.launch {
            delay(2000L) // Disable button for 2 seconds
            clearClipboard(context) // Clear the clipboard after the delay
            isButtonEnabled = true
        }
    }

    // Layout for the OTP screen
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 32.dp), // Adjusted padding to move everything down
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Section: Title and OTP
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "Able 3 Authenticator", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(16.dp)) // Spacer to add space between Title and OTP
            Text(text = otp.chunked(2).joinToString(" "), fontSize = 24.sp)
        }

        Spacer(modifier = Modifier.weight(1f)) // Spacer to push the bottom section down

        // Bottom Section: Expiration Info and Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(text = "Authcode will expire in", fontSize = 18.sp)
            Text(text = "$countdown", fontSize = 24.sp)
            Text(text = "second(s)", modifier = Modifier.padding(bottom = 24.dp), fontSize = 18.sp)
            Button(onClick = { handleCopyClick() }, enabled = isButtonEnabled) {
                Text("Copy Authcode")
            }
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

// Function to clear the clipboard
fun clearClipboard(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val emptyClip = ClipData.newPlainText("", "")
    clipboard.setPrimaryClip(emptyClip)
    Toast.makeText(context, "Clipboard cleared", Toast.LENGTH_SHORT).show()
}

@Preview(showBackground = true)
@Composable
fun OTPScreenPreview() {
    A3ATheme {
        OTPScreen()
    }
}