package com.smnexstudio.memora.logic

import android.content.Context
import com.smnexstudio.memora.data.EventType
import org.json.JSONObject

enum class Tone(val id: String, val label: String) { FORMAL("formal", "Formal"), FUN("fun", "Fun"), FAMILY("family", "Family") }

/**
 * Ready-made wishes from assets/templates.json. {name} and {age} are filled in
 * when the message is sent, so {age} stays right every year. Each wish has a
 * variant without {age}, used when the year isn't known.
 */
object Templates {
    private lateinit var root: JSONObject

    fun init(context: Context) {
        if (::root.isInitialized) return
        root = JSONObject(context.assets.open("templates.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    fun toneFrom(id: String) = Tone.entries.firstOrNull { it.id == id } ?: Tone.FORMAL

    fun templatesFor(type: EventType, tone: Tone, hasYear: Boolean): List<String> {
        val list = root.getJSONObject("templates").getJSONObject(type.id).getJSONObject(tone.id).getJSONArray("en")
        return List(list.length()) {
            val w = list.getJSONObject(it)
            if (hasYear) w.getString("text") else w.getString("noAge")
        }
    }

    fun belated(type: EventType): String = root.getJSONObject("belated").getJSONObject("en").getString(type.id)

    /** The suggested message for a new event. */
    fun default(type: EventType, hasYear: Boolean): String = templatesFor(type, Tone.FORMAL, hasYear).first()
}
