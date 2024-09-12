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
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Base32

val BrightBlue = Color(0xFF007FFF)
val BrightGreen = Color(0xFF00FF00)
val DarkOrange = Color(0xFF996300)
val LightOrange = Color(0xFFFFC14d)

fun isValidSecretKey(secretKey: String?): Boolean
{
    return try
    {
        val base32 = Base32()
        base32.decode(secretKey) != null
    }
    catch(e: Exception)
    {
        false
    }
}

fun loadData(sharedPreferences: SharedPreferences): List<Triple<String , String , Long>>
{
    val entries = sharedPreferences.getStringSet("otp_entries" , emptySet()) ?: emptySet()
    return entries.map {
        val parts = it.split("|")
        Triple(parts[0] , parts[1] , parts[2].toLong())
    }
}

fun saveData(otpEntries: List<Triple<String , String , Long>> , sharedPreferences: SharedPreferences)
{
    val entrySet = otpEntries.map { "${it.first}|${it.second}|${System.currentTimeMillis()}" }.toSet()
    sharedPreferences.edit().putStringSet("otp_entries" , entrySet).apply()
}

class MainActivity : FragmentActivity()
{

    private lateinit var sharedPreferences: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->

        if(isGranted)
        {
            startBarcodeScanner()
        }
        else
        {
            Toast.makeText(this , "Camera permission denied" , Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("Able3Studios" , Context.MODE_PRIVATE)

        val isDarkTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val statusBarColor = if(isDarkTheme) DarkOrange else LightOrange

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            window.statusBarColor = statusBarColor.toArgb()
        }

        authenticateUser()
    }

    @Composable
    fun A3ATheme(content: @Composable () -> Unit)
    {
        val colors = if(isSystemInDarkTheme())
        {
            darkColorScheme(primary = DarkOrange , onPrimary = Color.White , secondary = BrightGreen , onSecondary = Color.White)
        }
        else
        {
            lightColorScheme(primary = LightOrange , onPrimary = Color.Black , secondary = BrightBlue , onSecondary = Color.Black)
        }

        MaterialTheme(colorScheme = colors , content = content)
    }

    private fun authenticateUser()
    {
        val biometricManager = BiometricManager.from(this)

        when(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL))
        {
            BiometricManager.BIOMETRIC_SUCCESS -> showBiometricPrompt()
            else -> showDeviceCredentialPrompt()
        }
    }

    private fun requestCameraPermission()
    {
        if(ContextCompat.checkSelfPermission(this , Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        else
        {
            startBarcodeScanner()
        }
    }

    private fun showBiometricPrompt()
    {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(this , executor, object : BiometricPrompt.AuthenticationCallback()
        {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult)
            {
                super.onAuthenticationSucceeded(result)
                setContent { A3ATheme { MainScreen(onStartBarcodeScanner = { startBarcodeScanner() } , onRequestCameraPermission = { requestCameraPermission() } , sharedPreferences = sharedPreferences) } }
            }

            override fun onAuthenticationError(errorCode: Int , errString: CharSequence)
            {
                super.onAuthenticationError(errorCode , errString)
                showDeviceCredentialPrompt()
            }

            override fun onAuthenticationFailed()
            {
                super.onAuthenticationFailed()
                Toast.makeText(this@MainActivity , "Authentication Failed" , Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("Biometric Authentication").setSubtitle("Authenticate using biometrics").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun showDeviceCredentialPrompt()
    {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if(keyguardManager.isDeviceSecure)
        {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent("Device Security" , "Authenticate using PIN, Pattern or Password")

            if(intent != null)
            {
                startForResult.launch(intent)
            }
        }
        else
        {
            Toast.makeText(this , "No biometric or device credentials available" , Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBarcodeScanner()
    {
        val intent = Intent(this , BarcodeScannerActivity::class.java)
        startForResult.launch(intent)
    }

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    {
            result ->

        if(result.resultCode == RESULT_OK)
        {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            setContent { A3ATheme { MainScreen(onStartBarcodeScanner = { startBarcodeScanner() } , onRequestCameraPermission = { requestCameraPermission() } , barcode = barcode , sharedPreferences = sharedPreferences) } }
        }
        else
        {
            Toast.makeText(this , "Barcode scanning failed or canceled" , Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainScreen(onStartBarcodeScanner: () -> Unit , onRequestCameraPermission: () -> Unit , barcode: String? = null , sharedPreferences: SharedPreferences)
{
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Home" , "Barcode Scanner")
    var otpEntries by remember { mutableStateOf(loadData(sharedPreferences)) }
    var countdowns by remember { mutableStateOf(mutableMapOf<String, Int>()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteIcon by remember { mutableStateOf(false) }

    fun deleteOTP(websiteName: String)
    {
        otpEntries = otpEntries.filterNot { it.first == websiteName }.toMutableList()
        saveData(otpEntries , sharedPreferences)
        countdowns.remove(websiteName)
        showDeleteIcon = false
    }

    fun extractSecretKey(barcode: String): String?
    {
        val keyPattern = Regex("secret=([A-Za-z0-9]+)")
        val matchResult = keyPattern.find(barcode)
        val extractedKey = matchResult?.groups?.get(1)?.value

        Log.d("A3A" , "Extracted Secret Key: $extractedKey")

        if(extractedKey != null && isValidSecretKey(extractedKey))
        {
            return extractedKey
        }
        else
        {
            Log.e("A3A" , "Invalid Secret Key Extracted: $extractedKey")
            return null
        }
    }

    fun extractWebsiteName(barcode: String): String
    {
        return if(barcode.startsWith("otpauth://"))
        {
            val regex = Regex("""otpauth://[a-z]+/([^?]+)""")
            val matchResult = regex.find(barcode)
            val parts = matchResult?.groupValues?.get(1)?.split(":")
            val websiteName = parts?.firstOrNull()?.capitalize() ?: "Unknown Website"
            val userIdentifier = parts?.getOrNull(1) ?: ""

            if(userIdentifier.isNotEmpty())
            {
                "$websiteName : $userIdentifier"
            }
            else
            {
                "$websiteName"
            }
        }
        else
        {
            val domainRegex = Regex("""(?:https?://)?(?:www\.)?([a-zA-Z0-9.-]+)""")
            val matchResult = domainRegex.find(barcode)

            if(matchResult != null)
            {
                val domain = matchResult.groupValues[1]
                val websiteName = domain.split(".").firstOrNull()?.capitalize() ?: "Unknown Website"
                "$websiteName"
            }
            else
            {
                "Unknown Website"
            }
        }
    }

    fun generateHMACSHA1(key: ByteArray , data: ByteArray): ByteArray
    {
        val mac = Mac.getInstance("HmacSHA1")
        val secretKeySpec = SecretKeySpec(key , "HmacSHA1")
        mac.init(secretKeySpec)
        return mac.doFinal(data)
    }

    fun generateTOTP(secret: String): String
    {
        return try
        {
            val base32 = Base32()
            val secretKeyBytes = base32.decode(secret)
            val timeIndex = System.currentTimeMillis() / 1000 / 30
            val buffer = ByteBuffer.allocate(8)
            buffer.putLong(timeIndex)
            val hash = generateHMACSHA1(secretKeyBytes , buffer.array())
            val offset = hash[hash.size - 1].toInt() and 0xf
            val otpBinary = hash.copyOfRange(offset , offset + 4)
            otpBinary[0] = (otpBinary[0].toInt() and 0x7f).toByte()
            val otp = ByteBuffer.wrap(otpBinary).int
            String.format("%03d %03d" , otp % 1000000 / 1000 , otp % 1000)
        }
        catch(e: Exception)
        {
            println("Error generating TOTP for secret key: $secret")
            e.printStackTrace()
            "INVALID"
        }
    }

    fun startCountdown()
    {
        scope.launch {

            while(true)
            {
                delay(1000L)
                val updatedCountdowns = countdowns.toMutableMap()

                for((websiteName , countdown) in updatedCountdowns)
                {
                    if(countdown <= 0)
                    {
                        val otpEntry = otpEntries.find { it.first == websiteName }

                        if(otpEntry != null)
                        {
                            val newOtp = generateTOTP(otpEntry.second)
                            otpEntries = otpEntries.map {
                                if(it.first == websiteName) Triple(it.first , newOtp , System.currentTimeMillis())
                                else it
                            }.toMutableList()
                            saveData(otpEntries , sharedPreferences)
                        }

                        updatedCountdowns[websiteName] = 30
                    }
                    else
                    {
                        updatedCountdowns[websiteName] = countdown - 1
                    }
                }

                countdowns = updatedCountdowns
            }
        }
    }


    LaunchedEffect(barcode)
    {
        if(barcode != null)
        {
            val websiteName = extractWebsiteName(barcode)
            val secretKey = extractSecretKey(barcode)

            if(secretKey != null)
            {
                val generatedOtp = generateTOTP(secretKey)

                otpEntries = otpEntries.toMutableList().apply {
                    removeIf { it.first == websiteName }
                    add(Triple(websiteName , generatedOtp , System.currentTimeMillis()))
                }

                saveData(otpEntries , sharedPreferences)
                countdowns[websiteName] = 30
                startCountdown()
            }
            else
            {
                Toast.makeText(context , "Invalid barcode scanned" , Toast.LENGTH_SHORT).show()
            }
        }

        else if(otpEntries.isNotEmpty())
        {
            otpEntries.forEach { entry ->
                val elapsedTime = (System.currentTimeMillis() - entry.third) / 1000
                val remainingTime = 30 - (elapsedTime % 30).toInt()
                countdowns[entry.first] = remainingTime.coerceAtLeast(0)
            }

            startCountdown()
        }
    }

    Scaffold(bottomBar =
    {
        val navBarBackgroundColor = if(isSystemInDarkTheme()) DarkOrange else LightOrange

        NavigationBar(containerColor = navBarBackgroundColor)
        {
            items.forEachIndexed { index , item ->
                NavigationBarItem(
                    icon = {
                        when(index)
                        {
                            0 -> Icon(Icons.Filled.Home , contentDescription = null , modifier = Modifier.size(24.dp) , tint = if(selectedItem == index) Color.White else Color.Gray)
                            1 -> Icon(Icons.Filled.QrCodeScanner , contentDescription = null , modifier = Modifier.size(24.dp) , tint = if(selectedItem == index) Color.White else Color.Gray)
                        }
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick =
                    {
                        selectedItem = index

                        if(index == 1)
                        {
                            if(ContextCompat.checkSelfPermission(context , Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                            {
                                onStartBarcodeScanner()
                            }
                            else
                            {
                                onRequestCameraPermission()
                            }
                        }
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White , unselectedIconColor = Color.Gray , indicatorColor = Color.Transparent)
                )
            }
        }
    })
    { innerPadding ->

        Column(horizontalAlignment = Alignment.CenterHorizontally , modifier = Modifier.fillMaxSize().padding(innerPadding).pointerInput(Unit) { detectTapGestures(onTap = { showDeleteIcon = false }) })
        {
            Text(text = "Able 3 Authenticator", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(32.dp))

            val cardBackgroundColor = if(isSystemInDarkTheme()) DarkOrange else LightOrange

            Column(horizontalAlignment = Alignment.CenterHorizontally , modifier = Modifier.padding(16.dp))
            {
                if(otpEntries.isEmpty())
                {
                    Text(text = "No OTPs Yet" , fontSize = 24.sp)
                }
                else
                {
                    otpEntries.forEach { (websiteName , otp , _) ->

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp))
                        {
                            Text(text = websiteName , fontSize = 20.sp , modifier = Modifier.align(Alignment.Start))
                            Spacer(modifier = Modifier.height(4.dp))

                            Box(modifier = Modifier.fillMaxWidth())
                            {
                                SelectionContainer{
                                    DisableSelection {
                                        Text(text = "$otp" , fontSize = 24.sp , modifier = Modifier.align(Alignment.CenterStart).pointerInput(Unit)
                                        {
                                            detectTapGestures(onLongPress =
                                            {
                                                copyToClipboard(context , otp)
                                                showDeleteIcon = true
                                            })

                                        }.wrapContentWidth(Alignment.Start))
                                    }
                                }

                                if(showDeleteIcon)
                                {
                                    Icon(imageVector = Icons.Default.Delete , contentDescription = "Delete OTP" , modifier = Modifier.size(24.dp).align(Alignment.CenterEnd).pointerInput(Unit)
                                    {
                                        detectTapGestures(onTap =
                                        {
                                            deleteOTP(websiteName)
                                        })

                                    }, tint = cardBackgroundColor)
                                }

                                OTPCountdownCircle(countdown = countdowns[websiteName] ?: 30 , modifier = Modifier.size(24.dp).align(Alignment.CenterEnd).offset(x = (-40).dp))
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(cardBackgroundColor))
                        }
                    }
                }
            }
        }
    }
}

fun copyToClipboard(context: Context , text: String)
{
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OTP" , text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context , "OTP copied to clipboard" , Toast.LENGTH_SHORT).show()
}

@Composable
fun OTPCountdownCircle(countdown: Int , modifier: Modifier = Modifier , backgroundColor: Color = Color.LightGray)
{
    val isDarkTheme = isSystemInDarkTheme()
    val foregroundColor = if(isDarkTheme) BrightGreen else BrightBlue

    val totalDuration = 30
    val sweepAngle by remember(countdown) { mutableStateOf((countdown / totalDuration.toFloat()) * 360f) }

    Canvas(modifier = modifier)
    {
        drawArc(color = backgroundColor , startAngle = 0f , sweepAngle = 360f , useCenter = true)
        drawArc(color = foregroundColor , startAngle = -90f , sweepAngle = -sweepAngle , useCenter = true)
    }
}

@Preview(showBackground = true)
@Composable
fun OTPScreenPreview()
{
    A3ATheme { MainScreen(onStartBarcodeScanner = {} , onRequestCameraPermission = {} , sharedPreferences = LocalContext.current.getSharedPreferences("Able3Studios" , Context.MODE_PRIVATE)) }
}