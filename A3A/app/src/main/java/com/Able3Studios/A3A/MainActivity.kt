package com.Able3Studios.A3A

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.Manifest
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base32

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveOTP(otp: String, websiteName: String, secretKey: String) {
        sharedPreferences.edit().putString("otp", otp)
            .putString("websiteName", websiteName)
            .putString("secretKey", secretKey)
            .apply()
    }

    fun loadOTP(): Triple<String?, String?, String?> {
        val otp = sharedPreferences.getString("otp", null)
        val websiteName = sharedPreferences.getString("websiteName", null)
        val secretKey = sharedPreferences.getString("secretKey", null)
        return Triple(otp, websiteName, secretKey)
    }

    fun clearOTP() {
        sharedPreferences.edit().clear().apply()
    }

    // Function to extract website name from barcode
    fun extractWebsiteName(barcode: String): String {
        return when {
            barcode.contains("github", ignoreCase = true) -> "GitHub"
            barcode.contains("google", ignoreCase = true) -> "Google"
            // Add more website detection rules as needed
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

    fun generateTOTP(secret: String): String {
        val base32 = Base32()
        val secretKeyBytes = base32.decode(secret)
        val timeIndex = System.currentTimeMillis() / 1000 / 30
        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(timeIndex)
        val hash = HMACSHA1(secretKeyBytes, buffer.array())
        val offset = hash[hash.size - 1].toInt() and 0xf
        val otpBinary = hash.copyOfRange(offset, offset + 4)
        otpBinary[0] = (otpBinary[0].toInt() and 0x7f).toByte() // Force first bit of the binary string to be 0
        val otp = ByteBuffer.wrap(otpBinary).int
        return String.format("%06d", otp % 1000000)
    }

    fun startCountdown() {
        countdown = 30
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

    LaunchedEffect(barcode) {
        if (barcode != null) {
            websiteName = extractWebsiteName(barcode)
            secretKey = extractSecretKey(barcode) // Extract or store the secret key from the barcode
            otp = secretKey?.let { generateTOTP(it) }
            otp?.let { saveOTP(it, websiteName!!, secretKey!!) }
            startCountdown()  // Start countdown after generating OTP
        } else {
            val (loadedOtp, loadedWebsite, loadedSecret) = loadOTP()
            if (loadedOtp != null && loadedWebsite != null && loadedSecret != null) {
                otp = loadedOtp
                websiteName = loadedWebsite
                secretKey = loadedSecret
                startCountdown() // Start countdown if OTP is loaded from storage
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

                if (otp == null) {
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
                    Card(
                        modifier = Modifier.padding(16.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = "Website: $websiteName", fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "OTP: $otp", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Expires in $countdown seconds", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                copyToClipboard(context, otp!!)
                            }) {
                                Text("Copy Authcode")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                clearOTP() // Clear the stored OTP
                                otp = null // Delete the OTP
                            }) {
                                Text("Delete OTP")
                            }
                        }
                    }
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