package com.smnexstudio.memora.ui.screens

import com.smnexstudio.memora.ui.Route
import com.smnexstudio.memora.ui.rememberAgenda
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.Templates
import com.smnexstudio.memora.logic.daysUntil
import com.smnexstudio.memora.logic.eventLabel
import com.smnexstudio.memora.logic.fillMessage
import com.smnexstudio.memora.ui.Handwriting
import com.smnexstudio.memora.ui.Look
import com.smnexstudio.memora.ui.PaperCard
import com.smnexstudio.memora.ui.PostButton
import com.smnexstudio.memora.ui.RubberStamp
import com.smnexstudio.memora.ui.ScreenHeader
import com.smnexstudio.memora.ui.Stamp
import com.smnexstudio.memora.ui.dayTitle
import com.smnexstudio.memora.ui.rowTitle
import com.smnexstudio.memora.ui.stampText
import com.smnexstudio.memora.ui.theme

@Composable
fun MissedScreen(onBack: () -> Unit, navigate: (Route) -> Unit) {
    val t = theme
    val c = t.c
    val missed = rememberAgenda().missed

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Missed the post", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (missed.isEmpty()) "Nothing missed. Every card in the last two days went out."
                else "These passed in the last two days without a card. A belated one still counts.",
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp), fontSize = 15.sp, lineHeight = 22.sp, color = c.muted,
            )
            missed.forEachIndexed { i, (e, d) ->
                val late = -daysUntil(d)
                Box {
                    PaperCard(tilt = if (i % 2 == 0) -0.8f else 0.6f, padding = PaddingValues(16.dp), gap = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stamp(stampText(e, d), t.type(e.type), width = 44.dp, height = 52.dp)
                            Column {
                                Text(rowTitle(e), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                                Text("${eventLabel(e)}, ${if (late == 1) "yesterday" else dayTitle(d)}", fontSize = 13.sp, color = c.muted)
                            }
                        }
                        Handwriting(fillMessage(Templates.belated(e.type), e, d), size = 23)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PostButton("Send belated card", { navigate(Route.Approve.late(e.id, d)) }, Modifier.weight(1f), look = Look.STAMP, height = 44.dp)
                            PostButton("Mark skipped", { Repo.markSent(e.id, d.year, "skipped", null) }, look = Look.OUTLINE, height = 44.dp)
                        }
                    }
                    RubberStamp(
                        if (late == 1) "1 day late" else "$late days late", c.stamp,
                        Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp), tilt = if (i % 2 == 0) 8f else -6f,
                    )
                }
            }
            if (missed.isEmpty()) PostButton("Back to home", onBack, Modifier.fillMaxWidth(), look = Look.OUTLINE)
        }
    }
}
