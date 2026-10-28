package com.smnexstudio.memora

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
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
        // Debug builds log any disk or network work done on the main thread, the usual cause of jank and ANRs.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
        }
        Repo.init(this)
        GoogleHolidays.load()
        Templates.init(this)
        Reminders.createChannel(this)
        thread { Send.clearOldCards(this) }
        thread { Updates.onAppStart(this) }
    }
}
