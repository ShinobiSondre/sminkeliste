# Probe 4: exact fields for Boots (Loop54 V3), Vita (page JSON) and Blush/NordicFeel (product cards).
import json, re, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
Q = "maybelline mascara"; q = urllib.parse.quote(Q)
def req(u, data=None, extra=None):
    h = {"User-Agent": UA, "Accept": "text/html,application/json", "Accept-Language": "nb-NO"}
    h.update(extra or {})
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, data=data, headers=h), timeout=25)
        return r.status, r.read().decode("utf-8", "replace")
    except Exception as e:
        return getattr(e, "code", str(e)[:60]), (e.read().decode("utf-8","replace") if hasattr(e, "read") else "")
def note(t, s): print(f"::notice title={t}::{str(s)[:1900]}")

c, b = req("https://boots-no-prod.54proxy.com/search", json.dumps({"query": Q, "resultsOptions": {"take": 2}}).encode(),
           {"Content-Type": "application/json", "Api-Version": "V3"})
note(f"boots {c}", b[:1900]); note("boots more", b[1900:3800])

c, b = req(f"https://www.vita.no/search?q={q}")
ms = list(re.finditer(r'"displayName"\s*:\s*"[^"]*(?i:maybelline|mascara)[^"]*"', b))
note("vita hits", f"{len(ms)} displayName hits with the query")
for i, m in enumerate(ms[:1]):
    note(f"vita hit {i}", b[max(0, m.start()-1600): m.end()+200].replace("\n", " "))
for k in ['"searchResult', '"products"', '"productList', '"hits"', '"totalCount"']:
    i = b.find(k)
    if i >= 0: note(f"vita key {k}", b[max(0,i-200):i+300].replace("\n", " "))

c, b = req(f"https://www.nordicfeel.com/no/search?q={q}")
i = b.find('id="product-')
j = b.find('id="product-', i + 10)
card = b[i:j if j > 0 else i + 6000]
card = re.sub(r'srcset="[^"]*"', '', card)
note("nordicfeel card len", len(card))
note("nordicfeel card a", card[:1900]); note("nordicfeel card b", card[1900:3800]); note("nordicfeel card c", card[3800:5700])
