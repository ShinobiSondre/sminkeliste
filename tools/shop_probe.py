# Probe 2: what exactly the promising shops put in their pages.
import json, re, sys, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
q = urllib.parse.quote("maybelline mascara")
def get(u):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA, "Accept": "text/html,application/json", "Accept-Language": "nb-NO"}), timeout=25)
        return r.status, r.read().decode("utf-8", "replace")
    except Exception as e:
        return getattr(e, "code", str(e)[:60]), (e.read().decode("utf-8","replace") if hasattr(e, "read") else "")
def note(t, s): print(f"::notice title={t}::{s[:1800]}")
for name, u in {"vita": f"https://www.vita.no/search?q={q}", "nordicfeel": f"https://www.nordicfeel.com/no/search?q={q}",
                "kicks": f"https://www.kicks.no/search?q={q}", "apotek1": f"https://www.apotek1.no/sok?q={q}", "boots": f"https://www.boots.no/sok?q={q}",
                "bangerhead": f"https://www.bangerhead.no/sok?q={q}", "lyko": f"https://lyko.com/no/search?q={q}"}.items():
    code, b = get(u)
    note(f"{name} status", f"{code} len={len(b)}")
    for i, m in enumerate(re.findall(r'<script[^>]*ld\+json[^>]*>(.*?)</script>', b, re.S)[:3]):
        note(f"{name} ldjson{i}", m.strip()[:1500])
    for key in ["esales.apptus.cloud", "elevate", "clusterId", "cluster", "loop54", "algolia", "hello-retail", "helloretail", "searchApi", "apiUrl", "/api/"]:
        for m in re.finditer(re.escape(key), b, re.I):
            note(f"{name} ctx {key}", b[max(0, m.start()-150): m.end()+200].replace("\n", " "))
            break
    # Product-like snippets around a price
    for m in list(re.finditer(r'(?i)maybelline', b))[:3]:
        note(f"{name} near", b[max(0, m.start()-200): m.end()+300].replace("\n", " "))
