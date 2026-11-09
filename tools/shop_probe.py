# Probe 11: do these shops' public search pages show the products (name, price, picture) in the page itself?
import json, re, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
def get(u):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA, "Accept-Language": "nb-NO"}), timeout=25)
        return r.status, r.read().decode("utf-8", "replace")
    except Exception as e:
        return getattr(e, "code", str(e)[:50]), ""
def note(t, s): print(f"::notice title={t}::{str(s)[:1600]}")
q = urllib.parse.quote("huda beauty blush")
for name, u in [("beautycos", "https://www.beautycos.no/search?q=" + q), ("fredrikoglouisa", "https://www.fredrikoglouisa.no/search?q=" + q),
                ("boozt", "https://www.boozt.com/no/no/search/result?search_keyword=" + q)]:
    c, b = get(u)
    for i, m in enumerate(re.findall(r'<script[^>]*ld\+json[^>]*>(.*?)</script>', b, re.S)[:4]):
        if "Product" in m or "ItemList" in m: note(f"{name} ldjson {i}", m.strip()[:1500])
    for i, m in enumerate(list(re.finditer(r'(?i)huda', b))[:3]):
        note(f"{name} near {i}", re.sub(r"\s+", " ", b[max(0, m.start()-500): m.end()+700]))
    note(f"{name} {c}", f"len={len(b)} product-item={b.count('product-item')} price={b.count('price')}")
