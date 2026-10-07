package africa.samaos.clavier

import android.content.Context

/** Les emoji du clavier (maquette l1-emoji), les gestes et les personnes aux teintes de peau d'ici. */
object Emoji {
    class Categorie(val nom: String, val icone: String, val liste: List<String>)

    private fun l(s: String) = s.split(' ').filter { it.isNotBlank() }

    val CATEGORIES = listOf(
        Categorie(
            "Visages", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M8.5 14a4 4 0 0 0 7 0 M9 9.5h.01 M15 9.5h.01",
            l(
                "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😜 🤪 🤗 🤔 🤭 😶 😐 😏 😒 🙄 😬 😴 😷 🤒 🤕 😎 🤓 😕 😟 😮 😲 😳 🥺 😢 😭 😱 😤 😡 🥳 🤩 " +
                    "👍🏾 👎🏾 👏🏾 🙌🏾 🙏🏾 🤝🏾 💪🏾 👋🏾 ✌🏾 🤞🏾 👌🏾 ✋🏾 👊🏾 🤲🏾 👈🏾 👉🏾 ☝🏾 👍🏿 👏🏿 🙏🏿 " +
                    "👶🏾 👧🏾 👦🏾 👩🏾 👨🏾 👵🏾 👴🏾 🧕🏾 👮🏾 👩🏾‍⚕️ 👨🏾‍🏫 👩🏾‍🍳",
            ),
        ),
        Categorie(
            "Nourriture", "M4 12h16a8 8 0 0 1-16 0z M8 8c0-2 2-2 2-4 M13 8c0-2 2-2 2-4",
            l("🍲 🥘 🍛 🍚 🍗 🍖 🐟 🍳 🥭 🍌 🍍 🥥 🍉 🍊 🍋 🥑 🌽 🌶️ 🧅 🍅 🥜 🍠 🍞 🥖 ☕ 🍵 🧃 🥤 🍺 🍷 🎂 🍰 🍬 🍫"),
        ),
        Categorie(
            "Vie", "M4 11l8-6l8 6v9H4z M10 20v-5h4v5",
            l(
                "☀️ 🌤️ 🌧️ ⛈️ 🌙 ⭐ 🌳 🌴 🌺 🐔 🐐 🐄 🐕 🐈 🏠 🏫 🏥 ⛪ 🕌 🏪 🚕 🚌 🛵 🚗 🚲 ✈️ 🚢 ⚽ 🏀 🥁 🎵 🎶 📱 💻 📞 📚 ✏️ 📝 " +
                    "💰 💵 💳 🎁 🎉 🎊 🎓 💍 🕯️ ⏰ 🔑 🧺 🪣",
            ),
        ),
        Categorie(
            "Symboles", "M6 6h5v5H6z M13 13h5v5h-5z M14 6l4 5 M18 6l-4 5",
            l("❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💔 💕 ✅ ❌ ⚠️ ❓ ❗ 💯 🔥 ✨ 💫 🙈 🙉 🙊 💤 💬 🔔 📍 🇨🇮 🇲🇱 🇧🇫 🇸🇳 🇬🇳 🇬🇭"),
        ),
    )

    private fun prefs(c: Context) = c.getSharedPreferences("emoji", Context.MODE_PRIVATE)

    fun recents(c: Context): List<String> = prefs(c).getString("recents", "").orEmpty().split(' ').filter { it.isNotBlank() }

    fun utiliser(c: Context, e: String) {
        val l = (listOf(e) + recents(c).filter { it != e }).take(24)
        prefs(c).edit().putString("recents", l.joinToString(" ")).apply()
    }
}
