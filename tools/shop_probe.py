# Probe 9: as many Norwegian beauty web shops as possible — which can be read, and how.
import json, re, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
def req(u, data=None, extra=None, timeout=25):
    h = {"User-Agent": UA, "Accept": "text/html,application/json,*/*", "Accept-Language": "nb-NO,nb;q=0.9"}
    h.update(extra or {})
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, data=data, headers=h), timeout=timeout)
        return r.status, r.geturl(), r.read().decode("utf-8", "replace")
    except Exception as e:
        return getattr(e, "code", str(e)[:50]), u, (e.read().decode("utf-8","replace") if hasattr(e, "read") else "")
def note(t, s): print(f"::notice title={t}::{str(s)[:1500]}")
Q = urllib.parse.quote("huda beauty blush")
shops = {
  "notino": ["https://www.notino.no/search.asp?exps=" + Q, "https://www.notino.no/sok/?q=" + Q],
  "cocopanda": ["https://www.cocopanda.no/search?q=" + Q, "https://www.cocopanda.no/sok?q=" + Q],
  "fredrikoglouisa": ["https://www.fredrikoglouisa.no/search?q=" + Q],
  "bangerhead": ["https://www.bangerhead.no/sok?q=" + Q],
  "kicks": ["https://www.kicks.no/sok?q=" + Q],
  "apotek1": ["https://www.apotek1.no/sok?q=" + Q],
  "vitus": ["https://www.vitusapotek.no/sok?q=" + Q],
  "normal": ["https://www.normal.no/search?q=" + Q, "https://www.normal.no/sok?q=" + Q],
  "eleven": ["https://www.eleven.no/search?q=" + Q, "https://eleven.no/sok?q=" + Q],
  "sminkeriet": ["https://sminkeriet.no/search?q=" + Q],
  "lookfantastic": ["https://www.lookfantastic.no/search/?q=" + Q],
  "beautycos": ["https://www.beautycos.no/search?q=" + Q],
  "kosmetikkbaren": ["https://www.kosmetikkbaren.no/search?q=" + Q],
  "parfym": ["https://www.parfym.no/search?q=" + Q],
  "farmasiet": ["https://www.farmasiet.no/sok?q=" + Q],
  "boozt": ["https://www.boozt.com/no/no/search?search_keyword=" + Q],
  "zalando": ["https://www.zalando.no/catalog/?q=" + Q],
  "sephora": ["https://www.sephora.no/"],
  "douglas": ["https://www.douglas.no/"],
  "hm": ["https://www2.hm.com/no_no/search-results.html?q=" + Q],
  "cubus": ["https://cubus.com/nb-no/search?q=" + Q],
  "lindex": ["https://www.lindex.com/no/search?q=" + Q],
}
for name, urls in shops.items():
    for u in urls:
        c, f, b = req(u)
        low = b.lower()
        huda = low.count("huda")
        sig = [k for k in ["__NEXT_DATA__", "__NUXT", "CURRENT_PAGE", "ld+json", "algolia", "helloretail", "hello-retail", "loop54", "54proxy",
                           "esales", "apptus", "elevate", "klevu", "searchspring", "shopify", "magento", "doofinder", "clerk", "voyado", "dynamicyield", "nosto"] if k.lower() in low]
        prices = re.findall(r'"(?:price|salePrice|sellingPrice|currentPrice|finalPrice)"\s*:\s*\{?"?(?:value"?\s*:\s*|amount"?\s*:\s*)?"?([0-9]+[.,]?[0-9]*)', b)[:4]
        note(f"{name} {c}", f"{f[:90]} len={len(b)} huda={huda} prices={prices} sig={sig}")
        # Keys the shop's own search widgets use
        for pat in [r'(?i)algolia[^"]{0,40}"\s*:\s*\{[^}]{0,300}', r'(?i)"?(?:applicationId|appId|ALGOLIA_APP_ID)"?\s*[:=]\s*"([A-Z0-9]{8,12})"',
                    r'(?i)"?(?:searchApiKey|apiKey|ALGOLIA_API_KEY|searchKey)"?\s*[:=]\s*"([a-f0-9]{24,40})"', r'(?i)clusterId"?\s*[:=]\s*"([^"]+)"',
                    r'https://[a-z0-9-]+\.api\.esales\.apptus\.cloud', r'(?i)helloRetail[A-Za-z]*"?\s*[:=]\s*"([0-9a-f-]{20,40})"',
                    r'https://[a-z0-9.-]*(?:klevu|doofinder|clerk|searchspring|nosto|54proxy)[a-z0-9./-]*']:
            for m in re.finditer(pat, b):
                note(f"{name} key", m.group(0)[:300]); break
        if huda:
            m = re.search(r'(?i)huda', b[len(b)//4:])
            if m:
                k = len(b)//4 + m.start()
                note(f"{name} near", re.sub(r"\s+", " ", b[max(0, k-400):k+500]))
        if c == 200 and len(b) > 5000: break
