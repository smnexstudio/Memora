package com.smnexstudio.memora.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.Agenda
import com.smnexstudio.memora.logic.today

// Screens read what they show through these, so each one redraws only when its own data changes.

/** The shared [Agenda]: every event's next date, the missed ones, and what's on each day. */
@Composable
fun rememberAgenda(): Agenda = Agenda.of(Repo.events.collectAsState().value, today())

/** Ticks when sent history or person notes change; key a remember on it to re-read them. */
@Composable
fun historyVersion(): Int = Repo.history.collectAsState().value

/** Ticks when the downloaded festival dates change. */
@Composable
fun festivalVersion(): Int = Repo.festivalDates.collectAsState().value
