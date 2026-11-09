# Opens each shop, types into its own search box like a person would, and notes the search address it lands on
# and what the product cards look like there.
import json, sys, re
from playwright.sync_api import sync_playwright
HOMES = [("Kicks", "https://www.kicks.no/"), ("Bangerhead", "https://www.bangerhead.no/"), ("Fredrik & Louisa", "https://www.fredrikoglouisa.no/"),
         ("Apotek 1", "https://www.apotek1.no/"), ("Normal", "https://www.normalstores.com/no/"), ("Farmasiet", "https://www.farmasiet.no/"),
         ("Sminkeriet", "https://sminkeriet.no/"), ("Boozt", "https://www.boozt.com/no/no"), ("Notino", "https://www.notino.no/"),
         ("Blivakker", "https://www.blivakker.no/"), ("Lyko", "https://lyko.com/no"), ("Parfym.no", "https://www.parfym.no/")]
DIAG = """() => {
  const t = e => (e && (e.innerText || '') || '').replace(/\\s+/g, ' ').trim();
  const cards = [...document.querySelectorAll('a[href]')].filter(a => a.querySelector('img'))
    .map(a => { let c = a; for (let i = 0; i < 6 && c.parentElement; i++) { if (/\\d\\s*(kr|,-)|kr\\s*\\d/i.test(t(c))) break; c = c.parentElement; } return c; })
    .filter(c => /\\d\\s*(kr|,-)|kr\\s*\\d/i.test(t(c)) && t(c).length < 400);
  const clean = h => h.replace(/<svg[\\s\\S]*?<\\/svg>/g, '').replace(/ (srcset|sizes|style|data-[a-z-]+)="[^"]*"/g, '').slice(0, 1200);
  return JSON.stringify({ title: document.title, url: location.href, cards: cards.length, sample: cards.slice(1, 2).map(c => clean(c.outerHTML)), texts: cards.slice(0, 3).map(t) });
}"""
q = "maybelline mascara"
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(locale="nb-NO", viewport={"width": 412, "height": 900}, is_mobile=True,
        user_agent="Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
    for name, home in HOMES:
        pg = ctx.new_page()
        try:
            pg.goto(home, timeout=25000, wait_until="domcontentloaded"); pg.wait_for_timeout(3000)
            for txt in ["Godta alle", "Godta", "Aksepter alle", "Tillat alle", "Accept all", "Jeg forstår", "OK"]:
                btn = pg.get_by_role("button", name=re.compile("^" + txt, re.I))
                if btn.count(): 
                    try: btn.first.click(timeout=2000); break
                    except Exception: pass
            box = None
            for sel in ['input[type="search"]', 'input[name="q"]', 'input[name="query"]', 'input[placeholder*="øk" i]', 'input[aria-label*="øk" i]',
                        'button[aria-label*="øk" i]', '[data-testid*="search" i]', 'a[href*="sok"]', 'button[class*="search" i]']:
                el = pg.locator(sel).first
                if el.count():
                    try:
                        if not el.is_visible(): continue
                        if el.evaluate("e => e.tagName") != "INPUT":
                            el.click(timeout=2000); pg.wait_for_timeout(800)
                            el = pg.locator('input[type="search"], input[name="q"], input[placeholder*="øk" i]').first
                        el.fill(q, timeout=3000); el.press("Enter"); box = sel; break
                    except Exception as e:
                        continue
            pg.wait_for_timeout(8000)
            d = json.loads(pg.evaluate(DIAG))
            print(f"::notice title={name} search::via {box} → {d['url'][:140]} | {d['title'][:60]} | cards={d['cards']}")
            print(f"::notice title={name} texts::{d['texts']}"[:1200])
            for x in d["sample"]: print(f"::notice title={name} card::{x}")
        except Exception as e:
            print(f"::notice title={name}::failed {str(e)[:150]}")
        pg.close()
    b.close()
