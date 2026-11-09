// Reads the products off a shop's search page once it has loaded: name, price, before-price, picture, link.
// Used by the app's invisible browser, and by the cloud test that checks it against the real shops.
(function () {
  function txt(e) { return (e && (e.innerText || e.textContent) || '').replace(/\s+/g, ' ').trim(); }
  var NUM = '(\\d{1,3}(?:[ .\\u00a0]\\d{3})+(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)';
  var PRICE = new RegExp(NUM + '\\s*(?:kr|NOK|,-)|kr\\.?\\s*' + NUM, 'gi');
  // Prices in a piece of text, skipping "spar 32 kr", "kr 50 per 100 ml" and the like.
  function prices(s) {
    var r = [], m; PRICE.lastIndex = 0;
    while ((m = PRICE.exec(s))) {
      var before = s.slice(Math.max(0, m.index - 12), m.index).toLowerCase();
      var after = s.slice(m.index + m[0].length, m.index + m[0].length + 6).toLowerCase();
      if (/(spar|save|sparer|pr\.?|per|fra|-)\s*$/.test(before) && !/fra\s*$/.test(before)) continue;
      if (/^\s*(\/|pr|per)/.test(after)) continue;
      var v = (m[1] || m[2]).replace(/[ . ](?=\d{3}(\D|$))/g, '').replace(',', '.');
      var n = parseFloat(v);
      if (n >= 10 && n < 20000) r.push(n);
    }
    return r;
  }
  // Words around a product name that aren't part of it (buttons, labels, ratings).
  var JUNK = /^(gratis frakt|fri frakt|medlem|logg inn|store logo|logo|kjøp|legg i|gå til|se alle|vis|normale varer|flere varianter|nyhet|kampanje)/i;
  function clean(s) {
    return (s || '').replace(/(Bilde av|Gå til produktet|Legg til i ønskelisten|Legg i handlekurven|OVERVÅKE|Flere varianter|Kjøp|På nettlager.*$|Hent i \d+ apotek.*$|\(\d+\)|\d,\d \(\d+\))/gi, ' ')
      .replace(/^MERKE:\s*/i, '').replace(/\s+/g, ' ').trim();
  }
  var out = [], seen = {};
  function add(n, p, o, u, i) {
    if (!n || !p || !u || seen[u]) return;
    seen[u] = 1; out.push({ n: n, p: p, o: (o && o > p) ? o : null, u: u, i: (i || '').indexOf('http') === 0 ? i : '' });
  }
  // 1. Structured product data (schema.org), when the page has it.
  [].forEach.call(document.querySelectorAll('script[type="application/ld+json"]'), function (s) {
    try {
      var d = JSON.parse(s.textContent), list = [].concat(d['@graph'] || d);
      list.forEach(function (x) {
        var items = x['@type'] === 'ItemList' && x.itemListElement ? x.itemListElement.map(function (it) { return it.item || it; }) : (x['@type'] === 'Product' ? [x] : []);
        items.forEach(function (p) {
          var o = p.offers && [].concat(p.offers)[0]; var pr = o && parseFloat(o.price || o.lowPrice);
          var b = p.brand && (p.brand.name || p.brand); var n = p.name || '';
          if (b && typeof b === 'string' && n.toLowerCase().indexOf(b.toLowerCase()) < 0) n = b + ' ' + n;
          add(n, pr, null, p.url || (o && o.url), [].concat(p.image || [])[0]);
        });
      });
    } catch (e) {}
  });
  // 2. Product cards: a link with a picture, inside a box that shows a price.
  var links = document.querySelectorAll('a[href]');
  for (var k = 0; k < links.length && out.length < 60; k++) {
    var a = links[k], img = a.querySelector('img');
    var card = a;
    for (var d = 0; d < 7 && card.parentElement; d++) {
      if (prices(txt(card)).length && (img || card.querySelector('img'))) break;
      card = card.parentElement;
    }
    img = img || card.querySelector('img');
    var t = txt(card);
    if (!img || t.length > 700) continue;
    var href = a.href;
    if (!href || href.indexOf('http') !== 0 || seen[href] || /\/(sok|soek|search|s|kategori|category|merker|vare-merker|brands?|kampanjetilbud|kampanje)(\/|\?|$)/i.test(href.replace(/^https?:\/\/[^/]+/, ''))) continue;
    if (href.replace(/^https?:\/\/[^/]+/, '').replace(/[?#].*$/, '').length < 4) continue;
    var oldEl = card.querySelector('del, s, strike, [class*="old" i], [class*="before" i], [class*="original" i], [class*="compare" i], [class*="regular" i], [class*="value" i]');
    var oldP = oldEl ? prices(txt(oldEl))[0] : null;
    var priceEls = card.querySelectorAll('[class*="price" i]');
    var ps = [];
    [].forEach.call(priceEls, function (e) { if (!oldEl || !oldEl.contains(e) && !e.contains(oldEl) || e === oldEl.parentElement) ps = ps.concat(prices(txt(e))); });
    if (!ps.length) ps = prices(t);
    ps = ps.filter(function (x) { return x !== oldP; });
    if (!ps.length) continue;
    var nameEl = card.querySelector('[class*="productname" i], [class*="product-name" i], [class*="product__name" i], [class*="name" i]:not([class*="brand" i]), [class*="title" i], h2, h3, h4');
    var cands = [txt(nameEl), img.getAttribute('title'), a.getAttribute('title'), a.getAttribute('aria-label'), img.getAttribute('alt')]
      .map(clean).filter(function (x) { return x && x.length >= 4 && !JUNK.test(x); });
    var name = cands[0] || '';
    var brandEl = card.querySelector('[class*="brand" i]'), brand = clean(txt(brandEl));
    if (brand && brand.length < 40 && !JUNK.test(brand) && name.toLowerCase().indexOf(brand.toLowerCase()) < 0) name = brand + ' ' + name;
    // A shade or variant shown on its own line belongs to the name.
    var variant = clean(txt(card.querySelector('[class*="variant" i], [class*="shade" i], [class*="nyanse" i]')));
    if (variant && variant.length < 40 && !/\d\s*kr/i.test(variant) && name.toLowerCase().indexOf(variant.toLowerCase()) < 0) name = name + ' ' + variant;
    if (name.length < 6 || name.length > 160 || name.split(' ').length < 2) continue;
    var src = img.currentSrc || img.getAttribute('src') || img.getAttribute('data-src') || '';
    if (src.indexOf('http') !== 0) { var ss = (img.getAttribute('srcset') || img.getAttribute('data-srcset') || '').split(' ')[0]; if (ss) src = ss; }
    if (src.indexOf('//') === 0) src = 'https:' + src;
    add(name, Math.min.apply(null, ps), oldP, href, src);
  }
  return JSON.stringify(out);
})()
