# Probe: which beauty web shops answer a search with readable products and prices?
import json, re, sys, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
q = urllib.parse.quote(sys.argv[1] if len(sys.argv) > 1 else "maybelline mascara")
urls = {
  "lyko": f"https://lyko.com/no/search?q={q}",
  "kicks": f"https://www.kicks.no/search?q={q}",
  "vita": f"https://www.vita.no/sok?q={q}",
  "vita2": f"https://www.vita.no/search?q={q}",
  "blush": f"https://www.blush.no/search?q={q}",
  "bangerhead": f"https://www.bangerhead.no/sok?q={q}",
  "normal": f"https://www.normal.no/search?q={q}",
  "apotek1": f"https://www.apotek1.no/sok?q={q}",
  "vitus": f"https://www.vitusapotek.no/sok?q={q}",
  "boots": f"https://www.boots.no/sok?q={q}",
  "prisjakt": f"https://www.prisjakt.no/search?search={q}",
  "lyko_api": f"https://lyko.com/api/search?q={q}&market=no",
}
for name, u in urls.items():
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA, "Accept": "text/html,application/json", "Accept-Language": "nb-NO"}), timeout=25)
        b = r.read().decode("utf-8", "replace"); code = r.status; final = r.geturl()
    except Exception as e:
        b = getattr(e, "read", lambda: b"")().decode("utf-8", "replace") if hasattr(e, "read") else ""; code = getattr(e, "code", str(e)[:80]); final = u
    hints = [h for h in ["__NEXT_DATA__", "__NUXT__", "window.__INITIAL_STATE__", "algolia", "voyado", "elevate", "apptus", "klevu", "ld+json", "\"price\"", "\"Price\"", "kr", "graphql", "searchspring", "hello retail", "loop54"] if h.lower() in b.lower()]
    prices = re.findall(r'"(?:price|salePrice|currentPrice|sellingPrice)"\s*:\s*"?([0-9]+[.,]?[0-9]*)', b)[:6]
    names = re.findall(r'"(?:name|title|productName)"\s*:\s*"([^"]{6,70})"', b)[:6]
    apis = sorted(set(re.findall(r'https?://[a-z0-9.-]*(?:api|search|elevate|apptus|algolia|voyado|loop54|klevu)[a-z0-9.-]*\.[a-z]{2,}[^"\'\s<>]{0,60}', b)))[:6]
    print(f"::notice title={name} {code} {len(b)}b::final={final} hints={hints} prices={prices} names={names} apis={apis}")
