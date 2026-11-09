package com.sminkeliste.app

import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.WebPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** The shops' pages and answers as they looked when probed (trimmed). */
class WebShopTest {
    @Test fun prices() {
        assertEquals(286.2, Parse.priceText("286,20 kr")!!, 0.001)
        assertEquals(1234.5, Parse.priceText("1 234,50 kr")!!, 0.001)
        assertEquals(318.0, Parse.priceText("318 kr")!!, 0.001)
        assertNull(Parse.priceText("kr"))
    }

    @Test fun vita() {
        val html = """<html><script>window.CURRENT_PAGE = {   "searchPhrase": "maybelline mascara",   "products": [     {
            "url": "/maybelline-new-york/lash-sensational-sky-high-mascara/maybelline-lash-sensational-sky-high-mascara?v=363166",
            "images": [ { "width": 760, "height": 1130, "url": "/globalassets/produktbilder/30166967_0.jpg?ref=81DC4B7789" } ],
            "price": { "current": { "inclVat": 159.0 }, "original": { "inclVat": 189.0 } },
            "displayName": "Maybelline Lash Sensational Sky High Mascara", "brandName": "Maybelline New York" } ],
            "url": "/search?q=maybelline%20mascara" }; </script></html>"""
        val p = Parse.vitaProducts(html)
        assertEquals(1, p.size)
        assertEquals("Maybelline Lash Sensational Sky High Mascara", p[0].name)
        assertEquals(159.0, p[0].price, 0.001)
        assertEquals(189.0, p[0].prePrice!!, 0.001)
        assertEquals("https://www.vita.no/maybelline-new-york/lash-sensational-sky-high-mascara/maybelline-lash-sensational-sky-high-mascara?v=363166", p[0].url)
        assertEquals("https://www.vita.no/globalassets/produktbilder/30166967_0.jpg?ref=81DC4B7789", p[0].image)
    }

    @Test fun blush() {
        val html = """<div class="product-list__list"><div id="product-C33810-33810" aria-label="Maybelline Lash Sensational Waterproof Mascara 2-pk Duo Black" class="product-list-item__container product-list-item--detailed" data-brand="maybelline"><div class="product-list-item__image-container"><a href="https://www.nordicfeel.com/no/product/maybelline-lash-sensational-vannfast-mascara-2-pk-c33810-33810" rel="noopener noreferrer" class="product-list-item__image-link"><img onerror="this.setAttribute(&#39;data-error&#39;, 1)" width="240" height="240" alt="Lash Sensational" loading="lazy" data-nuxt-img srcset="x 1x" class="general-image product-list-item__image" src="https://www.nordicfeel.com/storage/a/P-C33810-33810.jpg?width=240&amp;height=240"></a><span class="discount-tag">-10%</span></div><a href="https://www.nordicfeel.com/no/product/maybelline-lash-sensational-vannfast-mascara-2-pk-c33810-33810" class="product-list-item__info-container"><div class="price price--large" inline="false"><div class="price__bottom"><div class="price__value">Verdi <span>318 kr</span></div><div class="price__now">286,20 kr</div></div><div class="price__save">Spar 32 kr</div></div></a></div><div id="product-C90420-90420" aria-label="Maybelline Sky High Waterproof Mascara" class="product-list-item__container"><a href="https://www.nordicfeel.com/no/product/sky-high-c90420"><div class="price__now">199 kr</div></a></div>"""
        val p = Parse.nordicfeelProducts(html)
        assertEquals(2, p.size)
        assertEquals("Maybelline Lash Sensational Waterproof Mascara 2-pk Duo Black", p[0].name)
        assertEquals(286.2, p[0].price, 0.001)
        assertEquals(318.0, p[0].prePrice!!, 0.001)
        assertEquals("https://www.nordicfeel.com/storage/a/P-C33810-33810.jpg?width=240&height=240", p[0].image)
        assertEquals(199.0, p[1].price, 0.001)
        assertNull(p[1].prePrice)
    }

    @Test fun boots() {
        val json = """{"results":{"count":64,"items":[{"type":"Product","id":"767087","attributes":[{"name":"Name","type":"string","values":["No7 Define And Enhance Mascara Black 7ml"]},{"name":"ProductURL","type":"string","values":["https://www.boots.no/no7-define-and-enhance-mascara-black-7ml-767087"]},{"name":"Brand","type":"string","values":["No7"]},{"name":"Price","type":"number","values":[229.9]},{"name":"ThumbnailURL","type":"string","values":["https://www.boots.no/media/t.jpg"]}]},{"type":"Product","id":"1","attributes":[{"name":"Name","type":"string","values":["Lash Sensational Sky High Mascara"]},{"name":"Brand","type":"string","values":["Maybelline"]},{"name":"Price","type":"number","values":[169]},{"name":"ProductURL","type":"string","values":["https://www.boots.no/sky-high"]}]}]}}"""
        val p = Parse.loop54Products(json)
        assertEquals(2, p.size)
        assertEquals("No7 Define And Enhance Mascara Black 7ml", p[0].name)
        assertEquals(229.9, p[0].price, 0.001)
        assertEquals("Maybelline Lash Sensational Sky High Mascara", p[1].name)
        assertEquals("https://www.boots.no/media/t.jpg", p[0].image)
    }

    @Test fun matchingKeepsWhatYouSearchedFor() {
        val all = listOf(
            WebPrice("Boots", "No7 Define And Enhance Mascara Black 7ml", 229.9, null, "u1", null),
            WebPrice("Vita", "Maybelline Lash Sensational Sky High Mascara", 189.0, null, "u2", null),
            WebPrice("Boots", "Maybelline Lash Sensational Sky High Mascara", 169.0, null, "u3", null),
            WebPrice("Blush", "Maybelline Fit Me Foundation", 129.0, null, "u4", null),
        )
        assertEquals(listOf("u3", "u2"), Parse.matchWeb(Parse.searchTerm("Maybelline mascara"), all).map { it.url })
    }

    @Test fun lyko() {
        val html = """<script>window.CURRENT_PAGE = {"searchPhrase":"huda blush","result":{"products":[{"url":"/no/house-of-hur/moist-ampoule-blusher-04","code":"4102-102-0004",
            "displayName":"House of Hur Moist Ampoule Blusher 04 Lavender Flush","price":{"current":124.5,"comparisonPrices":[{"type":"withoutCampaign","value":166.0}],"currency":"NOK"},
            "images":[{"height":2000,"width":1182,"url":"https://images.lyko.com/a/b.jpg"}]},
            {"url":"/no/huda-beauty/blush-filter","displayName":"Huda Beauty Blush Filter Liquid Blush Peach Sorbet","price":{"current":299.0,"comparisonPrices":[]},"images":[{"url":"/i/c.jpg"}]}]}}; </script>"""
        val p = Parse.lykoProducts(html)
        assertEquals(2, p.size)
        assertEquals(124.5, p[0].price, 0.001)
        assertEquals(166.0, p[0].prePrice!!, 0.001)
        assertEquals("https://lyko.com/no/house-of-hur/moist-ampoule-blusher-04", p[0].url)
        assertEquals("https://lyko.com/i/c.jpg", p[1].image)
        assertNull(p[1].prePrice)
        // "huda blush" finds the Huda Beauty blush, not House of Hur.
        assertEquals(listOf("Huda Beauty Blush Filter Liquid Blush Peach Sorbet"), Parse.matchWeb("huda blush", p).map { it.name })
    }

    @Test fun autofill() {
        assertTrue(Parse.suggest("hud").contains("Huda Beauty"))
        assertTrue(Parse.suggest("huda beauty bl").contains("Huda Beauty Blush"))
        assertTrue(Parse.suggest("masc").contains("Mascara"))
        assertTrue(Parse.suggest("mac").contains("MAC Cosmetics") || Parse.suggest("mac").contains("MAC"))
        assertFalse(Parse.suggest("cera").any { it.contains("Cerave", true) })
    }
}
