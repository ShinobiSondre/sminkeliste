package com.sminkeliste.app.data

import com.sminkeliste.app.util.Lang

/**
 * Beauty advice from ChatGPT (no login, no key), worded from your skin profile so the
 * suggestions and shades fit you. The answer ends with machine lines that become product cards.
 */
object Advice {
    private fun tail(no: Boolean) = if (no)
        "\nAvslutt svaret med én linje per produkt du anbefaler, nøyaktig slik: " +
            "«PRODUKT: <merke og produktnavn, og nyanse hvis det passer> | KATEGORI: <f.eks. foundation, mascara> | PRIS: <ca. pris i kr i Norge> | HVORFOR: <maks 8 ord>»."
    else
        "\nEnd the answer with one line per product you recommend, exactly like this: " +
            "\"PRODUCT: <brand and product name, and shade if it fits> | CATEGORY: <e.g. foundation, mascara> | PRICE: <approx. price in NOK in Norway> | WHY: <max 8 words>\"."

    private fun me(no: Boolean): String {
        val d = Profile.describe(no)
        return if (no) (if (d.isBlank()) "" else "Om meg: $d.\n") else (if (d.isBlank()) "" else "About me: $d.\n")
    }

    private fun common(no: Boolean, onSale: List<String>) = buildString {
        if (onSale.isNotEmpty()) append(if (no) "På tilbud nær meg denne uken (nevn det hvis noe passer): ${onSale.joinToString(", ")}\n"
            else "On sale near me this week (mention it if something fits): ${onSale.joinToString(", ")}\n")
        append(if (no) "Bare sminke (ikke hudpleie, hår eller parfyme). Velg produkter som er lette å få kjøpt i Norge (Vita, Kicks, Normal, Lyko, Blush, Boots, apotek). " +
            "Fortell hva folk med hud som min typisk liker og skryter av i anmeldelser, og hva de klager på. " +
            "Gi konkrete nyanseforslag når det gjelder farge, og si at nyansen bør testes i butikken. Svar på norsk, kort og oversiktlig."
        else "Makeup only (no skincare, hair or perfume). Pick products that are easy to buy in Norway (Vita, Kicks, Normal, Lyko, Blush, Boots, pharmacies). " +
            "Say what people with skin like mine typically love in reviews, and what they complain about. " +
            "Give concrete shade suggestions where colour matters, and say the shade should be tested in store. Keep it short and clear.")
    }

    /** "What should I get?" for one kind of product, with a budget, middle and luxury pick. */
    fun recommend(category: Category?, wish: String, onSale: List<String>): String {
        val no = Lang.no
        val what = category?.let { if (no) it.no.lowercase() else it.en.lowercase() } ?: if (no) "sminke" else "makeup"
        return buildString {
            append(if (no) "Hvilke $what-produkter passer best for meg? Foreslå 3–4: minst ett billig, ett mellomprisprodukt og ett luksusprodukt.\n"
                else "Which $what products suit me best? Suggest 3–4: at least one budget, one mid-range and one luxury product.\n")
            append(me(no))
            if (wish.isNotBlank()) append(if (no) "Ønsker: $wish\n" else "Wishes: $wish\n")
            append(common(no, onSale))
            append(tail(no))
        }
    }

    /** Cheaper alternatives ("dupes") for something you have or want. */
    fun dupes(product: String, onSale: List<String>): String {
        val no = Lang.no
        return buildString {
            append(if (no) "Finn 3 billigere alternativer (dupes) til «$product» som folk mener er nesten like gode. " +
                "Si hva som er likt og hva som er forskjellig, og om de passer for meg.\n"
            else "Find 3 cheaper alternatives (dupes) for \"$product\" that people say are almost as good. " +
                "Say what's the same, what's different, and whether they suit me.\n")
            append(me(no))
            append(common(no, onSale))
            append(tail(no))
        }
    }

    /** A simple routine for your skin: what, in which order, and why. */
    fun routine(look: String, onSale: List<String>): String {
        val no = Lang.no
        return buildString {
            append(if (no) "Lag en enkel sminkerutine for meg, steg for steg i riktig rekkefølge. Look: ${look.ifBlank { "hverdag" }}.\n"
                else "Make a simple makeup routine for me, step by step in the right order. Look: ${look.ifBlank { "everyday" }}.\n")
            append(me(no))
            append(common(no, onSale))
            append(tail(no))
        }
    }

    /** Does what's on my list suit me, and is anything missing? */
    fun checkList(list: List<String>, onSale: List<String>): String {
        val no = Lang.no
        return buildString {
            append(if (no) "Her er sminkelisten min: ${list.joinToString(", ")}. Passer dette for meg? Er det noe jeg bør bytte ut, " +
                "eller noe som mangler? Foreslå bedre eller billigere alternativer der det er lurt.\n"
            else "Here's my makeup list: ${list.joinToString(", ")}. Does it suit me? Should I swap anything, or is something missing? " +
                "Suggest better or cheaper alternatives where it makes sense.\n")
            append(me(no))
            append(common(no, onSale))
            append(tail(no))
        }
    }

    /** What the photo is for. */
    enum class PhotoGoal { FACE, LOOK, PRODUCT }

    /** A question about an attached photo: a face to match makeup to, a look to recreate, or a product to find. */
    fun photo(goal: PhotoGoal, wish: String, onSale: List<String>): String {
        val no = Lang.no
        return buildString {
            append(when (goal) {
                PhotoGoal.FACE -> if (no) "Se på ansiktet på bildet jeg har lagt ved. Beskriv hudtone, undertone, øyenfarge og hårfarge slik de ser ut, " +
                    "og foreslå sminke som passer: foundation- og concealernyanse, rouge/bronzer, leppefarger og øyenskyggefarger. " +
                    "Vær vennlig og saklig, uten å kommentere utseendet ellers. Si at lyset på bildet kan påvirke fargene.\n"
                else "Look at the face in the attached photo. Describe the skin tone, undertone, eye and hair colour as they appear, and suggest " +
                    "makeup that suits: foundation and concealer shade, blush/bronzer, lip colours and eyeshadow colours. Be kind and factual, " +
                    "with no other comments on looks. Mention that the lighting in the photo can change the colours.\n"
                PhotoGoal.LOOK -> if (no) "Bildet viser en sminkelook jeg vil gjenskape. Hvilke typer produkter og farger er brukt, i hvilken rekkefølge? " +
                    "Foreslå konkrete produkter for hvert steg.\n"
                else "The photo shows a makeup look I want to recreate. Which kinds of products and colours are used, in which order? " +
                    "Suggest concrete products for each step.\n"
                PhotoGoal.PRODUCT -> if (no) "Bildet viser et sminkeprodukt. Hvilket produkt er det (merke, navn og nyanse hvis du ser det)? " +
                    "Foreslå også 2 billigere alternativer.\n"
                else "The photo shows a makeup product. Which product is it (brand, name and shade if you can see it)? Also suggest 2 cheaper alternatives.\n"
            })
            append(if (no) "Hvis du ikke ser noe bilde, si det tydelig først.\n" else "If you can't see a photo, say so clearly first.\n")
            append(me(no))
            if (wish.isNotBlank()) append(if (no) "Ønsker: $wish\n" else "Wishes: $wish\n")
            append(common(no, onSale))
            append(tail(no))
        }
    }

    /** Your own question, with your profile attached. */
    fun free(question: String, onSale: List<String>): String {
        val no = Lang.no
        return buildString {
            append(question.trim()).append("\n")
            append(me(no))
            append(common(no, onSale))
            append(tail(no))
        }
    }
}
