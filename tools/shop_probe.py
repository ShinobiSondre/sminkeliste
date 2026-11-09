# Probe 6: more beauty web shops (for brands like Huda Beauty), and what today's three return for "huda blush".
import json, re, urllib.request, urllib.parse, uuid
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
Q = "huda blush"; q = urllib.parse.quote(Q)
def req(u, data=None, extra=None):
    h = {"User-Agent": UA, "Accept": "text/html,application/json", "Accept-Language": "nb-NO"}
    h.update(extra or {})
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, data=data, headers=h), timeout=25)
        return r.status, r.geturl(), r.read().decode("utf-8", "replace")
    except Exception as e:
        return getattr(e, "code", str(e)[:60]), u, (e.read().decode("utf-8","replace") if hasattr(e, "read") else "")
def note(t, s): print(f"::notice title={t}::{str(s)[:1800]}")

# Today's shops with the new query
c, f, b = req(f"https://www.vita.no/search?q={q}")
note("vita huda", f"{c} " + str(re.findall(r'"displayName"\s*:\s*"([^"]+)"', b)[:12]))
c, f, b = req(f"https://www.nordicfeel.com/no/search?q={q}")
note("blush huda", f"{c} " + str(re.findall(r'<div id="product-[^"]*" aria-label="([^"]+)"', b)[:12]))
c, f, b = req("https://boots-no-prod.54proxy.com/search", json.dumps({"query": Q, "resultsOptions": {"take": 5}}).encode(),
              {"Content-Type": "application/json", "Api-Version": "V3", "User-Id": str(uuid.uuid4())})
note("boots huda", f"{c} " + str(re.findall(r'"Name","type":"string","values":\["([^"]+)"', b)[:8]))

# New shops: home page → search form, search config, then likely search URLs
shops = {
  "kicks": ("https://www.kicks.no/", ["/sok?q=", "/search?q=", "/sok/?q=", "/search/?q=", "/sok?query="]),
  "lyko": ("https://lyko.com/no", ["/no/sok?q=", "/no/search?q=", "/no/sok?query=", "/no/search?query=", "/no/s?q="]),
  "bangerhead": ("https://www.bangerhead.no/", ["/sok?q=", "/search?q=", "/sok?query=", "/search?query="]),
  "cocopanda": ("https://www.cocopanda.no/", ["/search?q=", "/sok?q=", "/search?query="]),
  "fredrikoglouisa": ("https://www.fredrikoglouisa.no/", ["/search?q=", "/sok?q=", "/catalogsearch/result/?q="]),
  "lookfantastic": ("https://www.lookfantastic.no/", ["/elysium.search?search="]),
}
for name, (home, paths) in shops.items():
    c, f, b = req(home)
    forms = re.findall(r'<form[^>]*action="([^"]*)"[^>]*>', b)[:5]
    inputs = re.findall(r'<input[^>]*type="search"[^>]*>', b)[:2]
    cfg = []
    for k in ["esales.apptus.cloud", "clusterId", "elevate", "helloretail", "hello-retail", "websiteUuid", "algolia", "applicationId", "searchKey", "loop54", "54proxy", "klevu", "/api/search", "searchUrl"]:
        m = re.search(re.escape(k), b, re.I)
        if m: cfg.append(k + ": " + b[max(0, m.start()-120): m.end()+160].replace("\n", " "))
    note(f"{name} home {c}", f"final={f} len={len(b)} forms={forms} inputs={inputs}")
    for x in cfg[:5]: note(f"{name} cfg", x)
    root = re.match(r'https?://[^/]+', home).group(0)
    for p in paths:
        c, f, b = req(root + p + q)
        hits = len(re.findall(r'(?i)huda', b))
        pr = re.findall(r'"(?:price|salePrice|sellingPrice|currentPrice)"\s*:\s*\{?"?(?:value"?\s*:\s*|amount"?\s*:\s*)?"?([0-9]+[.,]?[0-9]*)', b)[:4]
        note(f"{name} {p} {c}", f"final={f} len={len(b)} huda={hits} prices={pr} next={'__NEXT_DATA__' in b} nuxt={'__NUXT' in b} ld={'ld+json' in b}")
