# Probe 7: Lyko's search page structure (product name, brand, price, image, link).
import re, urllib.request, urllib.parse
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
q = urllib.parse.quote("huda blush")
def note(t, s): print(f"::notice title={t}::{str(s)[:1900]}")
b = urllib.request.urlopen(urllib.request.Request(f"https://lyko.com/no/sok?q={q}", headers={"User-Agent": UA, "Accept-Language": "nb-NO"}), timeout=25).read().decode("utf-8", "replace")
for k in ["window.__", "__APOLLO", "__INITIAL", "self.__next_f", "application/json", "\"products\"", "\"price\""]:
    i = b.find(k)
    if i >= 0: note(f"lyko key {k}", b[max(0, i-200): i+500].replace("\n", " "))
for i, m in enumerate(list(re.finditer(r'(?i)huda', b))[:4]):
    note(f"lyko near {i}", b[max(0, m.start()-700): m.end()+900].replace("\n", " "))
