# Probe 3: real product data from the shops that looked readable (Boots, Vita, Blush/NordicFeel).
import json, re, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
Q = "maybelline mascara"; q = urllib.parse.quote(Q)
def req(u, data=None, ctype=None):
    h = {"User-Agent": UA, "Accept": "text/html,application/json", "Accept-Language": "nb-NO"}
    if ctype: h["Content-Type"] = ctype
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, data=data, headers=h), timeout=25)
        return r.status, r.read().decode("utf-8", "replace")
    except Exception as e:
        return getattr(e, "code", str(e)[:60]), (e.read().decode("utf-8","replace") if hasattr(e, "read") else "")
def note(t, s): print(f"::notice title={t}::{str(s)[:1900]}")

for path in ["/search", "/v3/search"]:
    c, b = req("https://boots-no-prod.54proxy.com" + path, json.dumps({"query": Q, "resultsOptions": {"take": 2}}).encode(), "application/json")
    note(f"boots loop54 {path} {c}", b[:1900])

c, b = req(f"https://www.vita.no/search?q={q}")
for i, m in enumerate(list(re.finditer(r'"price"', b))[:2]):
    note(f"vita price ctx {i}", b[max(0, m.start()-900): m.end()+300].replace("\n", " "))

c, b = req(f"https://www.nordicfeel.com/no/search?q={q}")
for i, m in enumerate(list(re.finditer(r'(?i)sky high|lash sensational|colossal', b))[:2]):
    note(f"nordicfeel card {i}", b[max(0, m.start()-900): m.end()+700].replace("\n", " "))
