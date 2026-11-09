# Loads each shop's public search page in a real browser (Chromium) and runs the app's reader script on it.
import json, sys, urllib.parse
from playwright.sync_api import sync_playwright
JS = open("app/src/main/assets/extract.js").read()
SHOPS = json.load(open("tools/shops.json"))
queries = sys.argv[1:] or ["huda beauty blush", "maybelline mascara"]
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(locale="nb-NO", viewport={"width": 412, "height": 900}, is_mobile=True,
        user_agent="Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
    for s in SHOPS:
        for q in queries:
            url = s["url"] + urllib.parse.quote(q)
            pg = ctx.new_page()
            try:
                pg.goto(url, timeout=25000, wait_until="domcontentloaded")
                best = []
                for _ in range(12):
                    pg.wait_for_timeout(1000)
                    r = json.loads(pg.evaluate(JS))
                    if len(r) > len(best): best = r
                    elif best: break
                sample = [(x["n"][:60], x["p"], x["o"], bool(x["i"])) for x in best[:6]]
                print(f"::notice title={s['name']} · {q}::{len(best)} products {sample}")
            except Exception as e:
                print(f"::notice title={s['name']} · {q}::failed {str(e)[:150]}")
            pg.close()
    b.close()
