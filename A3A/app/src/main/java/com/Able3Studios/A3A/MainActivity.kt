package com.Able3Studios.A3A

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.Able3Studios.A3A.ui.theme.A3ATheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Base32
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

val LightOrange = Color(0xFFFFC14d) // Bright orange for light theme
val DarkOrange = Color(0xFF996300) // Darker orange for dark theme

class MainActivity : FragmentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startBarcodeScanner()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("Able3Studios", Context.MODE_PRIVATE)

        // Set the status bar color based on the current theme (light or dark)
        val isDarkTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val statusBarColor = if (isDarkTheme) DarkOrange else LightOrange

        // Set the status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = statusBarColor.toArgb()
        }

        authenticateUser()
    }

    @Composable
    fun A3ATheme(content: @Composable () -> Unit) {
        val colors = if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = DarkOrange,
                onPrimary = Color.White
            )
        } else {
            lightColorScheme(
                primary = LightOrange,
                onPrimary = Color.Black
            )
        }

        MaterialTheme(
            colorScheme = colors,
            content = content
        )
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
                            onRequestCameraPermission = { requestCameraPermission() },
                            sharedPreferences = sharedPreferences
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
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            setContent {
                A3ATheme {
                    MainScreen(
                        onStartBarcodeScanner = { startBarcodeScanner() },
                        onRequestCameraPermission = { requestCameraPermission() },
                        barcode = barcode, // Pass the scanned barcode to MainScreen
                        sharedPreferences = sharedPreferences
                    )
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
    barcode: String? = null, // Accept the scanned barcode as a parameter
    sharedPreferences: SharedPreferences
) {
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Home", "Barcode Scanner")
    var otp by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf(30) }
    var websiteName by remember { mutableStateOf<String?>(null) }
    var secretKey by remember { mutableStateOf<String?>(null) } // To store the secret key
    var showDeleteIcon by remember { mutableStateOf(false) } // To control the visibility of delete icon
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Save secretKey and websiteName, NOT OTP
    fun saveData(websiteName: String, secretKey: String) {
        sharedPreferences.edit()
            .putString("websiteName", websiteName)
            .putString("secretKey", secretKey)
            .putLong("saved_time", System.currentTimeMillis())
            .apply()
    }

    // Load secretKey and websiteName
    fun loadData(): Pair<String?, String?> {
        val websiteName = sharedPreferences.getString("websiteName", null)
        val secretKey = sharedPreferences.getString("secretKey", null)
        return Pair(websiteName, secretKey)
    }

    // Function to extract website name from barcode
    fun extractWebsiteName(barcode: String): String {
        return when {
            barcode.contains("github", ignoreCase = true) -> "GitHub"
            barcode.contains("google", ignoreCase = true) -> "Google"
            else -> "Unknown Website"
        }
    }

    // Function to extract the secret key from the barcode
    fun extractSecretKey(barcode: String): String? {
        val keyPattern = Regex("secret=([A-Za-z0-9]+)")
        val matchResult = keyPattern.find(barcode)
        return matchResult?.groups?.get(1)?.value
    }

    fun HMACSHA1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA1")
        mac.init(secretKeySpec)
        return mac.doFinal(data)
    }

    // Generate OTP using TOTP algorithm with correct formatting
    fun generateTOTP(secret: String): String {
        val base32 = Base32()
        val secretKeyBytes = base32.decode(secret)
        val timeIndex = System.currentTimeMillis() / 1000 / 30
        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(timeIndex)
        val hash = HMACSHA1(secretKeyBytes, buffer.array())
        val offset = hash[hash.size - 1].toInt() and 0xf
        val otpBinary = hash.copyOfRange(offset, offset + 4)
        otpBinary[0] =
            (otpBinary[0].toInt() and 0x7f).toByte() // Force first bit of the binary string to be 0
        val otp = ByteBuffer.wrap(otpBinary).int
        return String.format("%03d %03d", otp % 1000000 / 1000, otp % 1000) // Format as XXX XXX
    }

    fun startCountdown() {
        scope.launch {
            while (true) {
                delay(1000L)
                countdown--
                if (countdown <= 0) {
                    otp = secretKey?.let { generateTOTP(it) }
                    countdown = 30
                }
            }
        }
    }

    // Handle app startup and restoring state
    LaunchedEffect(barcode) {
        if (barcode != null) {
            websiteName = extractWebsiteName(barcode)
            secretKey = extractSecretKey(barcode) // Extract the secret key from the barcode
            otp = secretKey?.let { generateTOTP(it) } // Generate OTP at start
            saveData(websiteName!!, secretKey!!)
            startCountdown()  // Start countdown after generating OTP
        } else {
            // Load saved secret key and website name
            val (loadedWebsite, loadedSecret) = loadData()
            if (loadedWebsite != null && loadedSecret != null) {
                websiteName = loadedWebsite
                secretKey = loadedSecret

                // Restore countdown based on saved time
                val savedTime = sharedPreferences.getLong("saved_time", 0L)
                val elapsedTime = (System.currentTimeMillis() - savedTime) / 1000
                countdown = (30 - (elapsedTime % 30)).toInt() // Calculate remaining time
                otp = secretKey?.let { generateTOTP(it) } // Generate OTP
                startCountdown()
            }
        }
    }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                showDeleteIcon = false
            }) // Reset delete icon visibility on tap
        },
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
                                0 -> { /* Do nothing for Home */
                                }

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

                // Move the Card logic out of the otp == null check
                Card(
                    modifier = Modifier.padding(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkOrange // Adaptive orange background based on the theme logic should be here
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = "Website: $websiteName", fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (otp == null) {
                            Text(text = "No OTPs Yet", fontSize = 24.sp)
                        } else {
                            // Display OTP and handle long-press for copy and delete
                            SelectionContainer {
                                DisableSelection {
                                    Text(
                                        text = "OTP: $otp",
                                        fontSize = 24.sp,
                                        modifier = Modifier.pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    // Copy OTP to clipboard on long press
                                                    copyToClipboard(context, otp!!)
                                                    showDeleteIcon = true
                                                }
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Show delete icon after long-press
                            if (showDeleteIcon) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete OTP",
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            sharedPreferences.edit().clear().apply()
                                            otp = null // Delete OTP
                                            showDeleteIcon = false
                                        })
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Universal countdown at the bottom
                if (otp != null) {
                    Text(
                        text = "Expires in $countdown seconds",
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
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
            onRequestCameraPermission = {},
            sharedPreferences = LocalContext.current.getSharedPreferences("Able3Studios", Context.MODE_PRIVATE)
        )
    }
}