# Sminkeliste 💄

A pink makeup list for Android, in Norwegian and English. Built like Handleliste.

- **Lists.** Wishlist, "running low" and your own lists. Items are grouped by kind (face, eyes, lips, nails, skincare …), with a shade or note on each.
- **Deals near you.** This week's flyers near you (eTilbudsavis/Tjek, free, no key) are searched for everything on your lists, and every makeup and skincare offer can be browsed by kind. Shows which shop has the most of your list on sale.
- **Online prices for comparison.** Under the shop deals: today's prices at Vita, Lyko, Kicks, Blush, Boots, Fredrik & Louisa and Parfym.no. Kicks, Fredrik & Louisa and Parfym.no only show their products in a browser, so the app opens their normal search page in an invisible browser and reads the products off it (assets/extract.js).
- **Deal alerts.** Follow a product, brand or kind (bell on an item, or in the Deals tab). The flyers are checked every 6 hours and you get a notification when it's on sale.
- **Your list pops up in the shop.** Walk into Vita, Kicks, Normal, a pharmacy and so on, and your list appears. Shops come from OpenStreetMap.
- **Beauty advice for your skin.** Fill in skin type, tone, undertone, concerns and wishes (vegan, fragrance-free, budget). ChatGPT (no login) suggests what people with skin like yours like, with shades, cheaper dupes and routines. Suggestions become cards you can add to the list, follow or price-check on Prisjakt.
- **Updates itself.** Every push to `main` builds an APK and publishes it under [Releases](../../releases). The app checks for a new release when it opens and installs it.

Your skin profile stays on the phone; it's only used to word the question to ChatGPT.
