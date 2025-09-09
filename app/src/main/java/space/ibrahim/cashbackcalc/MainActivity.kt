package space.ibrahim.cashbackcalc // Make sure this matches your project's package name

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log // Import Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// --- AlAhli Theme Definition ---

private val AlAhliGreen = Color(0xFF006A4E)
private val AlAhliGold = Color(0xFFFDB813)
private val AlAhliLightGray = Color(0xFFF0F0F0)

private val LightColorScheme = lightColorScheme(
    primary = AlAhliGreen,
    onPrimary = Color.White,
    secondary = AlAhliGold,
    onSecondary = Color.Black,
    background = AlAhliLightGray,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun AlAhliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dark theme can be customized here if needed, for now, it falls back to the light scheme.
    val colors = LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}


// --- Data class and Main Activity ---

// Data class to hold the summary for each month
data class MonthlySummary(
    val monthYear: String,
    val totalAmount: Float,
    val transactionCount: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply the custom AlAhli theme
            AlAhliTheme {
                CashbackTrackerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashbackTrackerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State variables for the UI
    var monthlySummaries by remember { mutableStateOf<List<MonthlySummary>>(emptyList()) }
    var totalCashback by remember { mutableStateOf(0.0f) }
    var isLoading by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Launcher for the permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permissionGranted = isGranted
        if (isGranted) {
            // Automatically analyze after permission is granted
            coroutineScope.launch {
                isLoading = true
                val (summaries, total) = readSms(context)
                monthlySummaries = summaries
                totalCashback = total
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AlAhli Cashback Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (permissionGranted) {
                        coroutineScope.launch {
                            isLoading = true
                            val (summaries, total) = readSms(context)
                            monthlySummaries = summaries
                            totalCashback = total
                            isLoading = false
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(text = "Analyze SMS for Cashback", color = MaterialTheme.colorScheme.onSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                Text(
                    text = "Total Cashback: %.2f SAR".format(totalCashback),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(monthlySummaries) { summary ->
                        MonthlySummaryCard(summary = summary)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlySummaryCard(summary: MonthlySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = summary.monthYear,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Total: %.2f SAR (%d transactions)".format(summary.totalAmount, summary.transactionCount),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// This function runs on a background thread to avoid blocking the UI
suspend fun readSms(context: Context): Pair<List<MonthlySummary>, Float> = withContext(Dispatchers.IO) {
    val TAG = "SmsReader"
    val monthlyCashback = LinkedHashMap<String, MutableList<Float>>()
    var totalCashback = 0.0f

    val inboxUri: Uri = Uri.parse("content://sms/inbox")

    // Use a LIKE query for a more flexible sender search
    val sender = "AlAhli" // Using a more general part of the name
    val selection = "address LIKE ?"
    val selectionArgs = arrayOf("%$sender%")

    Log.d(TAG, "Querying SMS from senders LIKE: $sender")
    val cursor: Cursor? = context.contentResolver.query(inboxUri, null, selection, selectionArgs, "date DESC")

    // Updated Regex to handle special characters between keyword and amount
    val saudiCashbackPattern = Pattern.compile("مبلغ[^\\d]*([\\d,]+(?:\\.\\d{1,2})?)\\s*SAR", Pattern.CASE_INSENSITIVE)

    cursor?.use {
        Log.d(TAG, "Cursor is valid. Found ${it.count} messages from senders LIKE $sender.")
        if (it.moveToFirst()) {
            val bodyIndex = it.getColumnIndex("body")
            val dateIndex = it.getColumnIndex("date")
            val senderIndex = it.getColumnIndex("address")
            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

            do {
                val message = it.getString(bodyIndex)
                val timestamp = it.getLong(dateIndex)
                val address = it.getString(senderIndex)
                var amountStr: String? = null

                Log.v(TAG, "Processing message from '$address': $message")

                // Check for the Saudi (SAR) format
                if (message.contains("استرجاع نقدي")) {
                    Log.d(TAG, "Found 'استرجاع نقدي' keyword.")
                    val matcher = saudiCashbackPattern.matcher(message)
                    if (matcher.find()) {
                        amountStr = matcher.group(1)
                        Log.d(TAG, "SAR Pattern matched. Found amount string: $amountStr")
                    } else {
                        Log.w(TAG, "Keyword found but SAR pattern did not match.")
                    }
                }

                // If an amount was found, process it
                if (amountStr != null) {
                    try {
                        val cleanedAmountStr = amountStr.replace(",", "")
                        val amount = cleanedAmountStr.toFloat()
                        totalCashback += amount

                        val monthYear = monthFormat.format(Date(timestamp))
                        monthlyCashback.getOrPut(monthYear) { mutableListOf() }.add(amount)
                        Log.i(TAG, "Successfully parsed amount: $amount for month: $monthYear")
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Could not parse amount string: $amountStr", e)
                    }
                }

            } while (it.moveToNext())
        } else {
            Log.w(TAG, "Cursor is empty, no messages to process from senders LIKE $sender.")
        }
    } ?: Log.e(TAG, "Cursor is null. Could not query SMS inbox.")

    val summaries = monthlyCashback.map { (month, amounts) ->
        MonthlySummary(month, amounts.sum(), amounts.size)
    }

    Log.i(TAG, "Finished processing. Total Cashback: $totalCashback. Found ${summaries.size} months.")

    return@withContext Pair(summaries, totalCashback)
}
