package com.sminkeliste.app

import com.sminkeliste.app.data.Offer
import com.sminkeliste.app.data.Parse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseTest {
    private fun offer(h: String, d: String = "", store: String = "Vita", price: Double = 99.0, pre: Double? = null, id: String = h) =
        Offer(id, h, d, store, price, pre, "NOK", "", null)

    @Test fun categories() {
        assertEquals("eyes", Parse.categoryOf("Maybelline Sky High mascara")?.key)
        assertEquals("lips", Parse.categoryOf("Leppestift rød")?.key)
        assertEquals("lips", Parse.categoryOf("Lip liner")?.key)
        assertEquals("face", Parse.categoryOf("Foundation 120 Ivory")?.key)
        assertEquals("eyes", Parse.categoryOf("Øyenskyggepalett")?.key)
        assertEquals("skin", Parse.categoryOf("Sminkefjerner")?.key)
        assertNull(Parse.categoryOf("Flip flops"))
    }

    @Test fun beautyOffers() {
        assertTrue(Parse.isBeauty(offer("MAYBELLINE LASH SENSATIONAL")))
        assertTrue(Parse.isBeauty(offer("MASCARA", "Flere varianter")))
        assertFalse(Parse.isBeauty(offer("KYLLINGFILET", "Solvinge, 400 g")))
        assertFalse(Parse.isBeauty(offer("FLIP CHIPS")))
    }

    @Test fun matching() {
        val offers = listOf(
            offer("MAYBELLINE SKY HIGH MASCARA", price = 129.0, id = "1"),
            offer("L'ORÉAL TELESCOPIC MASCARA", price = 149.0, id = "2"),
            offer("MAYBELLINE FIT ME FOUNDATION", price = 119.0, id = "3"),
            offer("KIWI MELK", store = "KIWI", price = 20.0, id = "4"),
        )
        val m = Parse.matchOffers(Parse.searchTerm("Maybelline mascara"), offers)
        assertEquals(listOf("1"), m.map { it.id })
        val all = Parse.matchOffers("mascara", offers)
        assertEquals(setOf("1", "2"), all.map { it.id }.toSet())
        assertEquals(listOf("1", "3"), Parse.matchOffers("maybelline", offers).map { it.id }.sorted())
    }

    @Test fun browseSortsByDiscount() {
        val offers = listOf(offer("NYX LIPSTICK", price = 80.0, pre = 100.0, id = "a"), offer("ESSENCE MASCARA", price = 40.0, pre = 80.0, id = "b"),
            offer("BANANER", store = "KIWI", id = "c"))
        assertEquals(listOf("b", "a"), Parse.beautyOffers(offers).map { it.id })
        assertEquals(listOf("a"), Parse.beautyOffers(offers, categoryKey = "lips").map { it.id })
    }

    @Test fun picks() {
        val a = """
            Her er forslagene mine.
            PRODUKT: Maybelline Fit Me Matte + Poreless, nyanse 115 | KATEGORI: foundation | PRIS: ca. 130 kr | HVORFOR: matt finish for fet hud
            **PRODUKT:** Fenty Pro Filt'r 150 | KATEGORI: foundation | PRIS: 450 kr | HVORFOR: stort nyanseutvalg
        """.trimIndent()
        val p = Parse.picks(a)
        assertEquals(2, p.size)
        assertEquals("Maybelline Fit Me Matte + Poreless, nyanse 115", p[0].name)
        assertEquals("foundation", p[0].category)
        assertEquals("ca. 130 kr", p[0].price)
        assertEquals("Fenty Pro Filt'r 150", p[1].name)
        assertEquals("Her er forslagene mine.", Parse.display(a))
    }

    @Test fun chains() {
        assertTrue(Parse.sellsBeauty("Vita"))
        assertTrue(Parse.sellsBeauty("Apotek 1"))
        assertTrue(Parse.sellsBeauty("Coop Obs"))
        assertFalse(Parse.sellsBeauty("Elkjøp"))
        assertTrue(Parse.sameChain("Vitusapotek Majorstuen", "Vitusapotek"))
    }

    @Test fun splitting() {
        assertEquals(listOf("Mascara", "Concealer", "Leppestift"), Parse.splitItems("mascara, concealer og leppestift"))
        assertEquals("maybelline mascara", Parse.searchTerm("2 stk Maybelline mascara!"))
    }
}
