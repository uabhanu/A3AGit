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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
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

        try {
            val packageManager = this.packageManager
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
                showDeviceCredentialPrompt()
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
            val scannedBarcode = result.data?.getStringExtra("SCANNED_BARCODE")
            scannedBarcode?.let {
                val otp = generateOTP()

                setContent {
                    A3ATheme {
                        MainScreen(
                            onStartBarcodeScanner = { startBarcodeScanner() },
                            onRequestCameraPermission = { requestCameraPermission() },
                            otp = otp // Pass the generated OTP to MainScreen
                        )
                    }
                }
            }
        } else {
            Toast.makeText(this, "Barcode scanning failed or canceled", Toast.LENGTH_SHORT).show()
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
        startForResult.launch(intent)
    }
}

@Composable
fun MainScreen(
    onStartBarcodeScanner: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    otp: String? = null // Accept the OTP as a parameter
) {
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Home", "Barcode Scanner")
    var otps by remember { mutableStateOf(listOf<Pair<String, Int>>()) } // List of OTPs with countdowns
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(otp) {
        if (otp != null) {
            otps = otps + Pair(otp, 10) // Add new OTP with countdown set to 10 seconds

            // Start countdown for the new OTP
            scope.launch {
                while (true) {
                    delay(1000L)
                    otps = otps.map { (currentOtp, countdown) ->
                        if (countdown > 1) {
                            Pair(currentOtp, countdown - 1) // Decrease countdown
                        } else {
                            Pair(generateOTP(), 10) // Reset OTP and countdown when it reaches zero
                        }
                    }
                }
            }
        }
    }

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
                                0 -> { /* Do nothing for Home */ }
                                1 -> {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        onStartBarcodeScanner()
                                    } else {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(text = "Able 3 Authenticator", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(32.dp))

                if (otps.isEmpty()) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Text(
                            text = "No OTPs Yet",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    otps.forEach { (otp, countdown) ->
                        Card(
                            modifier = Modifier.padding(16.dp),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(text = "OTP: $otp", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Expires in $countdown seconds", fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    copyToClipboard(context, otp)
                                }) {
                                    Text("Copy Authcode")
                                }
                            }
                        }
                    }
                }
            }
        }
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

@Preview(showBackground = true)
@Composable
fun OTPScreenPreview() {
    A3ATheme {
        MainScreen(
            onStartBarcodeScanner = {},
            onRequestCameraPermission = {}
        )
    }
}