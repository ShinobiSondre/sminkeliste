package com.sminkeliste.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sminkeliste.app.util.tr

/** A choice with a Norwegian and English name, and (for skin tones) a colour swatch. */
data class Opt(val key: String, val en: String, val no: String, val swatch: Long? = null) {
    val label: String get() = tr(en, no)
}

/**
 * Your skin, as you describe it. It's only kept on the phone and only used to word the question
 * to the AI, so the suggestions (and shades) fit you.
 */
object Profile {
    val SKIN_TYPES = listOf(
        Opt("normal", "Normal", "Normal"), Opt("dry", "Dry", "Tørr"), Opt("oily", "Oily", "Fet"),
        Opt("combo", "Combination", "Kombinert"), Opt("sensitive", "Sensitive", "Sensitiv"),
    )
    /** Skin depth, from the lightest to the deepest (the scale shade ranges use). */
    val TONES = listOf(
        Opt("fair", "Very fair", "Veldig lys", 0xFFF6E1D3), Opt("light", "Light", "Lys", 0xFFEECBB0),
        Opt("lightmed", "Light-medium", "Lys-medium", 0xFFE0B08C), Opt("medium", "Medium", "Medium", 0xFFC98F67),
        Opt("tan", "Tan / olive", "Gyllen / oliven", 0xFFAD7449), Opt("deep", "Deep", "Mørk", 0xFF7D4E2E),
        Opt("rich", "Very deep", "Veldig mørk", 0xFF4E2F1C),
    )
    val UNDERTONES = listOf(
        Opt("cool", "Cool (pink)", "Kjølig (rosa)"), Opt("warm", "Warm (golden)", "Varm (gyllen)"),
        Opt("neutral", "Neutral", "Nøytral"), Opt("olive", "Olive", "Oliven"), Opt("unsure", "Not sure", "Vet ikke"),
    )
    val CONCERNS = listOf(
        Opt("acne", "Breakouts", "Kviser / uren hud"), Opt("redness", "Redness", "Rødhet"), Opt("pigment", "Dark spots", "Pigmentflekker"),
        Opt("pores", "Large pores", "Store porer"), Opt("lines", "Fine lines", "Fine linjer"), Opt("circles", "Dark circles", "Mørke ringer"),
        Opt("dull", "Dull skin", "Matt / glansløs hud"), Opt("shine", "Shine", "Blank hud"),
    )
    val EYES = listOf(
        Opt("blue", "Blue", "Blå"), Opt("green", "Green", "Grønn"), Opt("hazel", "Hazel", "Nøttebrun"),
        Opt("brown", "Brown", "Brun"), Opt("grey", "Grey", "Grå"),
    )
    val WISHES = listOf(
        Opt("vegan", "Vegan", "Vegansk"), Opt("crueltyfree", "Cruelty-free", "Ikke testet på dyr"),
        Opt("fragrancefree", "Fragrance-free", "Parfymefri"), Opt("natural", "Natural look", "Naturlig look"),
        Opt("glam", "Glam look", "Glam-look"), Opt("longwear", "Long-lasting", "Lang holdbarhet"),
    )
    val BUDGETS = listOf(
        Opt("low", "Budget", "Billig"), Opt("mid", "Mid-range", "Mellom"), Opt("high", "Luxury", "Luksus"), Opt("any", "Mix", "Blanding"),
    )

    var skin by mutableStateOf<String?>(null); private set
    var tone by mutableStateOf<String?>(null); private set
    var undertone by mutableStateOf<String?>(null); private set
    var eyes by mutableStateOf<String?>(null); private set
    var budget by mutableStateOf("any"); private set
    var concerns by mutableStateOf<Set<String>>(emptySet()); private set
    var wishes by mutableStateOf<Set<String>>(emptySet()); private set
    var age by mutableStateOf(""); private set
    var done by mutableStateOf(false); private set

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        val p = p(ctx)
        skin = p.getString("skin", null); tone = p.getString("tone", null); undertone = p.getString("under", null)
        eyes = p.getString("eyes", null); budget = p.getString("budget", "any") ?: "any"
        concerns = p.getStringSet("concerns", emptySet())!!.toSet(); wishes = p.getStringSet("wishes", emptySet())!!.toSet()
        age = p.getString("age", "").orEmpty(); done = p.getBoolean("done", false)
    }

    fun save(ctx: Context, skin: String?, tone: String?, undertone: String?, eyes: String?, budget: String,
             concerns: Set<String>, wishes: Set<String>, age: String) {
        p(ctx).edit().putString("skin", skin).putString("tone", tone).putString("under", undertone).putString("eyes", eyes)
            .putString("budget", budget).putStringSet("concerns", concerns).putStringSet("wishes", wishes)
            .putString("age", age.trim()).putBoolean("done", true).apply()
        init(ctx)
    }

    fun skip(ctx: Context) { p(ctx).edit().putBoolean("done", true).apply(); done = true }

    private fun name(list: List<Opt>, key: String?) = list.firstOrNull { it.key == key }

    /** A short summary for the screen: "Kombinert · Lys · Kjølig". */
    fun summary(): String = listOfNotNull(name(SKIN_TYPES, skin)?.label, name(TONES, tone)?.label,
        name(UNDERTONES, undertone)?.takeIf { it.key != "unsure" }?.label).joinToString(" · ")

    val isEmpty: Boolean get() = skin == null && tone == null && undertone == null && concerns.isEmpty()

    /** The profile as a sentence for the AI, in the app's language. */
    fun describe(no: Boolean): String {
        fun l(o: Opt?) = o?.let { if (no) it.no else it.en }?.lowercase()
        val b = mutableListOf<String>()
        if (no) {
            l(name(SKIN_TYPES, skin))?.let { b += "hudtype: $it" }
            l(name(TONES, tone))?.let { b += "hudtone: $it" }
            name(UNDERTONES, undertone)?.takeIf { it.key != "unsure" }?.let { b += "undertone: ${l(it)}" }
            if (concerns.isNotEmpty()) b += "hudutfordringer: " + CONCERNS.filter { it.key in concerns }.joinToString(", ") { it.no.lowercase() }
            l(name(EYES, eyes))?.let { b += "øyenfarge: $it" }
            if (age.isNotBlank()) b += "alder: $age"
            if (wishes.isNotEmpty()) b += "ønsker: " + WISHES.filter { it.key in wishes }.joinToString(", ") { it.no.lowercase() }
            l(name(BUDGETS, budget))?.takeIf { budget != "any" }?.let { b += "budsjett: $it" }
        } else {
            l(name(SKIN_TYPES, skin))?.let { b += "skin type: $it" }
            l(name(TONES, tone))?.let { b += "skin tone: $it" }
            name(UNDERTONES, undertone)?.takeIf { it.key != "unsure" }?.let { b += "undertone: ${l(it)}" }
            if (concerns.isNotEmpty()) b += "skin concerns: " + CONCERNS.filter { it.key in concerns }.joinToString(", ") { it.en.lowercase() }
            l(name(EYES, eyes))?.let { b += "eye colour: $it" }
            if (age.isNotBlank()) b += "age: $age"
            if (wishes.isNotEmpty()) b += "wishes: " + WISHES.filter { it.key in wishes }.joinToString(", ") { it.en.lowercase() }
            l(name(BUDGETS, budget))?.takeIf { budget != "any" }?.let { b += "budget: $it" }
        }
        return b.joinToString("; ")
    }
}
