package com.sleepyhead.app.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.qr.QrCodes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CodesScreen(data: AppData, onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        TopBar("QR codes", onBack)
        Text(
            "Print these codes and tape each one in its place. The bathroom code goes where you brush your teeth. " +
                "Put them where you cannot scan them from bed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { (context as? Activity)?.let { QrCodes.print(it, data.steps) } },
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.Print, null); Spacer(Modifier.size(8.dp)); Text("Print or save as PDF")
        }
        data.steps.forEachIndexed { i, step ->
            val bmp = remember(step.code) { QrCodes.bitmap(step.code, 512).asImageBitmap() }
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Step ${i + 1}: ${step.title}", style = MaterialTheme.typography.titleMedium)
                    Text(step.place, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.padding(top = 12.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp)
                    ) {
                        Image(bmp, "QR code for ${step.title}", Modifier.size(220.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun HistoryScreen(data: AppData, onBack: () -> Unit, modifier: Modifier) {
    val today = LocalDate.now()
    val streak = Routine.streak(data, today)
    val best = Routine.bestStreak(data)
    val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val last30 = (0 until 30).map { today.minusDays(it.toLong()) }
    val onTime30 = last30.count { data.logs[it.toString()]?.onTime == true }
    val routineDays30 = last30.count { Routine.isRoutineDay(data.settings, it) }

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        TopBar("Your mornings", onBack)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("$streak", "current streak")
            Stat("$best", "best streak")
            Stat("$onTime30/$routineDays30", "on time, 30 days")
        }
        HorizontalDivider()
        last30.forEach { day ->
            val log = data.logs[day.toString()]
            val routineDay = Routine.isRoutineDay(data.settings, day)
            if (!routineDay && log == null) return@forEach
            val status = when {
                log?.completedAtMillis != null -> {
                    val t = Instant.ofEpochMilli(log.completedAtMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
                    if (log.onTime) "✓ At desk $t" else "◐ At desk $t (late)"
                }
                log?.skipped == true -> "– Skipped"
                day == today -> "… In progress"
                else -> "✗ Not finished"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(day.format(dateFmt), Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(status, color = if (log?.onTime == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if ((log?.blockedAttempts ?: 0) > 0) {
                        Text(
                            "Reached for blocked apps ${log!!.blockedAttempts}×",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
