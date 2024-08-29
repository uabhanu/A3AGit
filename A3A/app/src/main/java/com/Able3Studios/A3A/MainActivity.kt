package com.Able3Studios.A3A

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.Able3Studios.A3A.ui.theme.A3ATheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : FragmentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startBarcodeScanner()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safe access to PackageManager with logging
        try {
            val packageManager = this.packageManager
            // Additional initialization logic, if any
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during initialization: ${e.message}")
        }

        authenticateUser()
    }

    private fun authenticateUser() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> showBiometricPrompt()
            else -> showDeviceCredentialPrompt()
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                setContent {
                    A3ATheme {
                        MainScreen(
                            onStartBarcodeScanner = { startBarcodeScanner() },
                            onRequestCameraPermission = { requestCameraPermission() }
                        )
                    }
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                showDeviceCredentialPrompt() // Fallback to device credentials if biometrics fails
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@MainActivity, "Authentication Failed", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Authenticate using biometrics")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showDeviceCredentialPrompt() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isDeviceSecure) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent("Device Security", "Authenticate using PIN, Pattern or Password")
            if (intent != null) {
                startForResult.launch(intent)
            }
        } else {
            Toast.makeText(this, "No biometric or device credentials available", Toast.LENGTH_SHORT).show()
        }
    }

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setContent {
                A3ATheme {
                    MainScreen(
                        onStartBarcodeScanner = { startBarcodeScanner() },
                        onRequestCameraPermission = { requestCameraPermission() }
                    )
                }
            }
        } else {
            Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startBarcodeScanner()
        }
    }

    private fun startBarcodeScanner() {
        val intent = Intent(this, BarcodeScannerActivity::class.java)
        startActivity(intent)
    }
}

@Composable
fun MainScreen(
    onStartBarcodeScanner: () -> Unit,
    onRequestCameraPermission: () -> Unit
) {
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Home", "Barcode Scanner")

    val context = LocalContext.current // Obtain the context here

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Filled.Home, contentDescription = null)
                                1 -> Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                            }
                        },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
                            selectedItem = index
                            when (index) {
                                0 -> {
                                    // Home Screen, do nothing as this is the default
                                }
                                1 -> {
                                    // Check for camera permission
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        onStartBarcodeScanner()
                                    } else {
                                        // Request permission if not granted
                                        onRequestCameraPermission()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedItem) {
                0 -> OTPScreen()
                // 1 -> Barcode Scanner Activity is launched directly, so no UI here.
            }
        }
    }
}

@Composable
fun OTPScreen(modifier: Modifier = Modifier) {
    var otp by remember { mutableStateOf(generateOTP()) }
    var countdown by remember { mutableStateOf(10) }
    val isButtonEnabled = remember { mutableStateOf(true) }
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
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "Able 3 Authenticator", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = otp.chunked(2).joinToString(" "), fontSize = 24.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(text = "Authcode will expire in", fontSize = 18.sp)
            Text(text = "$countdown", fontSize = 24.sp)
            Text(text = "second(s)", modifier = Modifier.padding(bottom = 24.dp), fontSize = 18.sp)
            Button(onClick = { handleCopyClick(context, otp, coroutineScope, isButtonEnabled) }, enabled = isButtonEnabled.value) {
                Text("Copy Authcode")
            }
        }
    }
}

fun handleCopyClick(context: Context, otp: String, coroutineScope: CoroutineScope, isButtonEnabled: MutableState<Boolean>) {
    copyToClipboard(context, otp)
    isButtonEnabled.value = false // Update MutableState
    coroutineScope.launch {
        delay(2000L)
        clearClipboard(context)
        isButtonEnabled.value = true // Re-enable button
    }
}

fun generateOTP(): String {
    return (Random.nextInt(900000) + 100000).toString()
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OTP", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "OTP copied to clipboard", Toast.LENGTH_SHORT).show()
}

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
        MainScreen(
            onStartBarcodeScanner = {},
            onRequestCameraPermission = {}
        ) // Preview with MainScreen
    }
}