package space.ibrahim.cashbackcalc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val colors = LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}


// --- Data class and Main Activity ---

data class MonthlySummary(
    val monthYear: String,
    val totalAmount: Float,
    val transactionCount: Int,
    val spendingAmount: Float = 0f,
    val spendingCount: Int = 0
)

data class SmsReadResult(
    val summaries: List<MonthlySummary>,
    val totalCashback: Float,
    val totalSpending: Float
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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

    var monthlySummaries by remember { mutableStateOf<List<MonthlySummary>>(emptyList()) }
    var totalCashback by remember { mutableStateOf(0.0f) }
    var isLoading by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permissionGranted = isGranted
        // readSms will be triggered by the LaunchedEffect reacting to permissionGranted changing
    }

    // Auto-load: fires on first composition and whenever permissionGranted flips to true
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && monthlySummaries.isEmpty() && !isLoading) {
            isLoading = true
            val result = readSms(context)
            monthlySummaries = result.summaries
            totalCashback = result.totalCashback
            isLoading = false
        }
    }

    val onAnalyzeClick: () -> Unit = {
        if (permissionGranted) {
            coroutineScope.launch {
                isLoading = true
                val result = readSms(context)
                monthlySummaries = result.summaries
                totalCashback = result.totalCashback
                isLoading = false
            }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AlAhli Cashback Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (monthlySummaries.isNotEmpty() && !isLoading) {
                        IconButton(onClick = onAnalyzeClick) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                monthlySummaries.isEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "No cashback data yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap below to scan your SMS messages\nfor AlAhli cashback notifications.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = onAnalyzeClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = "Analyze SMS",
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Hero summary card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Total Cashback",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = String.format(Locale.US, "SAR %,.2f", totalCashback),
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${monthlySummaries.size} months · ${monthlySummaries.sumOf { it.transactionCount }} transactions",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    val totalSpending = monthlySummaries.sumOf { it.spendingAmount.toDouble() }.toFloat()
                                    if (totalSpending > 0f) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = String.format(Locale.US, "%.2f%% cashback rate", totalCashback / totalSpending * 100f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Monthly summary cards
                        items(monthlySummaries) { summary ->
                            MonthlySummaryCard(summary = summary)
                        }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent bar: split green/red when spending present, solid green otherwise
            if (summary.spendingCount > 0) {
                Column(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            // Content column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Text(
                    text = summary.monthYear,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Cashback row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${summary.transactionCount} cashback transaction${if (summary.transactionCount != 1) "s" else ""}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (summary.spendingAmount > 0f) {
                            val pct = summary.totalAmount / summary.spendingAmount * 100f
                            Text(
                                text = String.format(Locale.US, "%.2f%%", pct),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "SAR %,.2f", summary.totalAmount),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End
                        )
                    }
                }
                // Spending row (conditional)
                if (summary.spendingCount > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${summary.spendingCount} purchase${if (summary.spendingCount != 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "SAR %,.2f", summary.spendingAmount),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// Runs on a background thread to avoid blocking the UI
suspend fun readSms(context: Context): SmsReadResult = withContext(Dispatchers.IO) {
    val TAG = "SmsReader"
    val monthlyCashback = LinkedHashMap<String, MutableList<Float>>()
    val monthlySpending = LinkedHashMap<String, MutableList<Float>>()
    var totalCashback = 0.0f
    var totalSpending = 0.0f

    val inboxUri: Uri = Uri.parse("content://sms/inbox")
    val sender = "AlAhli"
    val selection = "address LIKE ?"
    val selectionArgs = arrayOf("%$sender%")

    Log.d(TAG, "Querying SMS from senders LIKE: $sender")
    val cursor: Cursor? = context.contentResolver.query(inboxUri, null, selection, selectionArgs, "date DESC")

    val mablaghSarPattern = Pattern.compile("مبلغ[^\\d]*([\\d,]+(?:\\.\\d{1,2})?)[\\s\\p{Cf}]*SAR")
    val beSarPattern = Pattern.compile("بـ[\\p{Cf}]*([\\d,]+(?:\\.\\d{1,2})?)[\\s\\p{Cf}]*SAR")

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
                Log.v(TAG, "Processing message from '$address': $message")

                when {
                    message.contains("استرجاع نقدي") -> {
                        val matcher = mablaghSarPattern.matcher(message)
                        if (matcher.find()) {
                            try {
                                val amount = matcher.group(1)!!.replace(",", "").toFloat()
                                totalCashback += amount
                                val monthYear = monthFormat.format(Date(timestamp))
                                monthlyCashback.getOrPut(monthYear) { mutableListOf() }.add(amount)
                                Log.i(TAG, "Cashback: $amount for $monthYear")
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "Could not parse cashback: ${matcher.group(1)}", e)
                            }
                        } else Log.w(TAG, "Cashback keyword found but pattern did not match.")
                    }

                    message.contains("شراء") -> {
                        val m1 = mablaghSarPattern.matcher(message)
                        val m2 = beSarPattern.matcher(message)
                        val amountStr = when {
                            m1.find() -> m1.group(1)
                            m2.find() -> m2.group(1)
                            else -> null
                        }
                        if (amountStr != null) {
                            try {
                                val amount = amountStr.replace(",", "").toFloat()
                                totalSpending += amount
                                val monthYear = monthFormat.format(Date(timestamp))
                                monthlySpending.getOrPut(monthYear) { mutableListOf() }.add(amount)
                                Log.i(TAG, "Spending: $amount for $monthYear")
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "Could not parse spending: $amountStr", e)
                            }
                        } else Log.w(TAG, "Spending keyword found but no pattern matched.")
                    }
                }
            } while (it.moveToNext())
        } else {
            Log.w(TAG, "Cursor is empty, no messages to process from senders LIKE $sender.")
        }
    } ?: Log.e(TAG, "Cursor is null. Could not query SMS inbox.")

    val allMonths = LinkedHashSet<String>().apply {
        addAll(monthlyCashback.keys)
        addAll(monthlySpending.keys)
    }
    val summaries = allMonths.map { month ->
        val ca = monthlyCashback[month] ?: emptyList()
        val sp = monthlySpending[month] ?: emptyList()
        MonthlySummary(month, ca.sum(), ca.size, sp.sum(), sp.size)
    }

    Log.i(TAG, "Finished processing. Total Cashback: $totalCashback. Total Spending: $totalSpending. Found ${summaries.size} months.")
    return@withContext SmsReadResult(summaries, totalCashback, totalSpending)
}
