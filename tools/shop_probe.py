# Probe 8: does Lyko's real page match what the app reads? (window.CURRENT_PAGE → a "products" list)
import json, re, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
def note(t, s): print(f"::notice title={t}::{str(s)[:1800]}")
for q in ["huda blush", "fenty foundation", "rare beauty blush"]:
    b = urllib.request.urlopen(urllib.request.Request("https://lyko.com/no/sok?q=" + urllib.parse.quote(q), headers={"User-Agent": UA, "Accept-Language": "nb-NO"}), timeout=25).read().decode("utf-8", "replace")
    i = b.find("window.CURRENT_PAGE"); o = b.find("{", i); e = b.find("</script>", o)
    try: page = json.loads(b[o:e].strip().rstrip(";").strip())
    except Exception as x: note(f"lyko {q}", f"json error {x}"); continue
    found = []
    def walk(n, path, d=0):
        if d > 6: return
        if isinstance(n, dict):
            p = n.get("products")
            if isinstance(p, list) and p and isinstance(p[0], dict) and "displayName" in p[0]: found.append((path, p))
            for k, v in n.items(): walk(v, path + "." + k, d + 1)
        elif isinstance(n, list):
            for j, v in enumerate(n[:50]): walk(v, f"{path}[{j}]", d + 1)
    walk(page, "")
    path, prods = found[0] if found else ("-", [])
    note(f"lyko {q}", f"at {path}: " + str([(p.get("displayName"), (p.get("price") or {}).get("current"), p.get("url", "")[:40]) for p in prods[:6]]))
