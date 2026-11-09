# Probe 5: Boots (Loop54 V3 with User-Id) and the price markup in a Blush/NordicFeel product card.
import json, re, urllib.request, urllib.parse, uuid
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
           {"Content-Type": "application/json", "Api-Version": "V3", "User-Id": str(uuid.uuid4())})
note(f"boots {c}", b[:1900]); note("boots more", b[1900:3800])

c, b = req(f"https://www.nordicfeel.com/no/search?q={q}")
i = b.find('class="product-list-item__container')
j = b.find('class="product-list-item__container', i + 10)
card = re.sub(r'(srcset|src|style|onerror)="[^"]*"', '', b[i:j if j > 0 else i + 8000])
card = re.sub(r'<svg.*?</svg>', '', card, flags=re.S)
note("nordicfeel card len", len(card))
for k in range(0, min(len(card), 5700), 1900): note(f"nordicfeel card {k}", card[k:k+1900])
