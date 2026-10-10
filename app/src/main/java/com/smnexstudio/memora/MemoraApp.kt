package com.smnexstudio.memora

import android.app.Application
import com.smnexstudio.memora.data.Repo
import com.smnexstudio.memora.logic.GoogleHolidays
import com.smnexstudio.memora.logic.Templates
import com.smnexstudio.memora.platform.Send
import com.smnexstudio.memora.platform.Updates
import com.smnexstudio.memora.reminders.Reminders
import kotlin.concurrent.thread

class MemoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        GoogleHolidays.load()
        Templates.init(this)
        Reminders.createChannel(this)
        thread { Send.clearOldCards(this) }
        thread { Updates.onAppStart(this) }
    }
}
