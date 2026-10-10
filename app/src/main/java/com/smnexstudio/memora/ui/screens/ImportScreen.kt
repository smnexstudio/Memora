package com.smnexstudio.memora.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.MemoraEvent
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.MONTHS
import com.smnexstudio.memora.logic.Templates
import com.smnexstudio.memora.platform.ContactDate
import com.smnexstudio.memora.platform.ContactsRepo
import com.smnexstudio.memora.reminders.Reminders
import com.smnexstudio.memora.ui.BottomBar
import com.smnexstudio.memora.ui.DashedDivider
import com.smnexstudio.memora.ui.Dot
import com.smnexstudio.memora.ui.Fraunces
import com.smnexstudio.memora.ui.LinkText
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.ScreenHeader
import com.smnexstudio.memora.ui.paper
import com.smnexstudio.memora.ui.theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

private val COUNT_WORDS = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
private fun count(n: Int) = COUNT_WORDS.getOrNull(n) ?: n.toString()

@Composable
fun ImportScreen(onBack: () -> Unit) {
    val t = theme
    val c = t.c
    val context = LocalContext.current
    var asked by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ContactDate>?>(null) }
    var filed by remember { mutableStateOf(setOf<String>()) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> denied = !ok; asked++ }

    LaunchedEffect(asked) {
        if (!ContactsRepo.hasAccess(context)) {
            if (asked == 0) permission.launch(Manifest.permission.READ_CONTACTS)
            return@LaunchedEffect
        }
        val done = Repo.importedContactIds()
        val found = withContext(Dispatchers.IO) { ContactsRepo.dates(context) }
        filed = found.map { it.key }.filter { it in done }.toSet()
        items = found.sortedBy { it.key in done }
        picked = found.map { it.key }.filter { it !in done }.toSet()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Import from contacts", onBack)
        when {
            denied -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text("Contacts access is off", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c.ink)
                Text("Allow Memora to read contacts to bring in saved birthdays. Nothing leaves your phone.", fontSize = 15.sp, color = c.muted, textAlign = TextAlign.Center)
                PostButton("Open settings", {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, Modifier.padding(top = 12.dp))
            }
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.stamp) }
            else -> {
                val list = items!!
                val fresh = list.filter { it.key !in filed }
                val allOn = fresh.isNotEmpty() && picked.size == fresh.size
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                    Text(
                        when {
                            list.isEmpty() -> "No birthdays or anniversaries are saved in your contacts."
                            filed.isEmpty() -> "Found ${count(list.size)} date${if (list.size == 1) "" else "s"} on your phone."
                            fresh.isEmpty() -> "All ${list.size} dates on your phone are already in Memora."
                            else -> "Found ${count(list.size)} dates on your phone. ${count(filed.size).replaceFirstChar { it.uppercase() }} ${if (filed.size == 1) "is" else "are"} already in Memora, so ${if (filed.size == 1) "it’ll" else "they’ll"} be skipped."
                        },
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp), fontSize = 15.sp, lineHeight = 22.sp, color = c.muted,
                    )
                    if (fresh.isNotEmpty()) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${picked.size} selected", Modifier.weight(1f), fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = c.ink)
                        LinkText(if (allOn) "Select none" else "Select all", { picked = if (allOn) emptySet() else fresh.map { it.key }.toSet() })
                    }
                    if (list.isNotEmpty()) Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().paper(c.paper, c.shadow)) {
                        list.forEachIndexed { i, x ->
                            if (i > 0) DashedDivider()
                            val done = x.key in filed
                            val on = x.key in picked
                            Row(
                                Modifier.fillMaxWidth().then(if (done) Modifier.background(c.paperAlt) else Modifier.clickable(role = Role.Checkbox) {
                                    picked = if (on) picked - x.key else picked + x.key
                                }).padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Checkbox(on, null, enabled = !done, colors = CheckboxDefaults.colors(checkedColor = c.ink, checkmarkColor = c.onInk))
                                Column(Modifier.weight(1f)) {
                                    Text(x.name, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Bold, fontSize = 15.sp, color = if (done) c.muted else c.ink)
                                    Text(
                                        "${x.type.label}, ${x.day} ${MONTHS[x.month - 1].take(3)}${x.year?.let { " $it" } ?: ", year unknown"}${if (x.phone == null && !done) ", no phone" else ""}",
                                        fontSize = 13.sp, color = c.muted,
                                    )
                                }
                                if (done) RubberStamp("Already filed", c.muted, tilt = -5f, fontSize = 11) else Dot(t.type(x.type), 8)
                            }
                        }
                    }
                }
                if (fresh.isNotEmpty()) BottomBar {
                    PostButton(
                        if (picked.isEmpty()) "Pick at least one" else "Import ${picked.size} event${if (picked.size == 1) "" else "s"}", {
                            val offsets = Repo.defaultOffsets()
                            Repo.saveEvents(
                                list.filter { it.key in picked }.map { x ->
                                    MemoraEvent(
                                        id = 0, type = x.type, name = x.name, title = null, month = x.month, day = x.day, year = x.year,
                                        phone = x.phone, email = x.email, message = Templates.default(x.type, x.year != null),
                                        offsets = offsets, notes = null, contactId = x.key, lastSentYear = null,
                                    )
                                },
                            )
                            val app = context.applicationContext
                            thread { Reminders.syncAll(app) }
                            onBack()
                        }, Modifier.fillMaxWidth(), look = Look.STAMP, height = 52.dp, fontSize = 16, enabled = picked.isNotEmpty(),
                    )
                }
            }
        }
    }
}
