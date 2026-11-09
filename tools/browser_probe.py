# Loads each shop's public search page in a real browser and shows what a product card looks like there.
import json, sys, urllib.parse
from playwright.sync_api import sync_playwright
SHOPS = json.load(open("tools/shops.json"))
DIAG = """() => {
  const t = e => (e && (e.innerText || '') || '').replace(/\\s+/g, ' ').trim();
  const cards = [...document.querySelectorAll('a[href]')].filter(a => a.querySelector('img'))
    .map(a => { let c = a; for (let i = 0; i < 6 && c.parentElement; i++) { if (/\\d\\s*(kr|,-)|kr\\s*\\d/i.test(t(c))) break; c = c.parentElement; } return c; })
    .filter(c => /\\d\\s*(kr|,-)|kr\\s*\\d/i.test(t(c)) && t(c).length < 400);
  const clean = h => h.replace(/<svg[\\s\\S]*?<\\/svg>/g, '').replace(/ (srcset|sizes|style|data-[a-z-]+)="[^"]*"/g, '').slice(0, 1400);
  return JSON.stringify({ title: document.title, url: location.href, body: t(document.body).slice(0, 300), cards: cards.length,
    sample: cards.slice(2, 3).map(c => clean(c.outerHTML)), texts: cards.slice(0, 4).map(t) });
}"""
q = sys.argv[1] if len(sys.argv) > 1 else "maybelline mascara"
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(locale="nb-NO", viewport={"width": 412, "height": 900}, is_mobile=True,
        user_agent="Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
    for s in SHOPS:
        pg = ctx.new_page()
        try:
            pg.goto(s["url"] + urllib.parse.quote(q), timeout=25000, wait_until="domcontentloaded")
            pg.wait_for_timeout(9000)
            d = json.loads(pg.evaluate(DIAG))
            print(f"::notice title={s['name']} page::{d['title'][:80]} | {d['url'][:100]} | cards={d['cards']} | body={d['body'][:250]}")
            print(f"::notice title={s['name']} texts::{d['texts']}"[:1500])
            for x in d["sample"]: print(f"::notice title={s['name']} card::{x}")
        except Exception as e:
            print(f"::notice title={s['name']}::failed {str(e)[:150]}")
        pg.close()
    b.close()
