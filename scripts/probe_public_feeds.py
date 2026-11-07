"""Contrôle non intrusif de sources publiques sans afficher de publications de personnes."""
import json
from urllib.request import Request, urlopen
from urllib.parse import urlencode
from urllib.error import HTTPError, URLError
import xml.etree.ElementTree as ET

def check(name, url, kind):
    try:
        request=Request(url,headers={"User-Agent":"FranceClimRadar13/2.2 (test source publique)"})
        with urlopen(request,timeout=15) as response:
            raw=response.read(350_000)
            status=response.status
            ctype=response.headers.get("Content-Type","")
        if kind=="json":
            obj=json.loads(raw)
            count=len(obj.get("posts",[]))
        else:
            doc=ET.fromstring(raw)
            count=sum(e.tag.rsplit("}",1)[-1].lower() in ("item","entry") for e in doc.iter())
        print(f"{name}: HTTP {status}, {count} éléments, format {kind}, {ctype[:35]}")
        return True
    except Exception as exc:
        label=getattr(exc,"code",type(exc).__name__)
        print(f"{name}: indisponible, {label} (aucun contenu personnel affiché)")
        return False

def main():
    params=urlencode({"q":"climatisation","sort":"latest","lang":"fr","limit":5})
    checks=[
        ("Bluesky",f"https://api.bsky.app/xrpc/app.bsky.feed.searchPosts?{params}","json"),
        ("Mastodon #climatisation","https://mastodon.social/tags/climatisation.rss","xml"),
        ("Mastodon #chauffeeau","https://mastodon.social/tags/chauffeeau.rss","xml"),
    ]
    results=[check(*item) for item in checks]
    print(f"SOURCES ACTIVES : {sum(results)}/{len(results)}. Ceci ne garantit pas de clients réels.")

def audit_qualified():
    """Ne journalise aucun auteur, texte, identifiant ni contact personnel."""
    from radar13.bluesky import QUERIES, fetch_search
    from radar13.engine import classify
    from radar13.run import parse_feed
    posts_seen = set()
    bsky_total = bsky_potential = 0
    for term in QUERIES:
        try:
            posts = fetch_search(term)
        except Exception as exc:
            print(f"Bluesky {term}: échec {type(exc).__name__}")
            continue
        for item in posts:
            key = item["id"]
            if key in posts_seen:
                continue
            posts_seen.add(key)
            bsky_total += 1
            result = classify(item["text"])
            if result and result["score"] >= 70:
                bsky_potential += 1
    print(f"QUALIFICATION BLUESKY : {bsky_total} textes uniques examinés, {bsky_potential} pistes potentielles dans le 13.")

    rss_total = rss_potential = 0
    for tag in ("climatisation", "chauffeeau"):
        try:
            url = f"https://mastodon.social/tags/{tag}.rss"
            req = Request(url, headers={"User-Agent":"FranceClimRadar13/2.2 (diagnostic public)"})
            with urlopen(req,timeout=15) as response:
                data = response.read(600_000)
            items = parse_feed(data)
            for item in items:
                rss_total += 1
                match = classify(item["text"])
                if match and match["score"] >= 70:
                    rss_potential += 1
        except Exception as exc:
            print(f"Mastodon #{tag}: échec {type(exc).__name__}")
    print(f"QUALIFICATION MASTODON : {rss_total} publications examinées, {rss_potential} pistes potentielles dans le 13.")
    print("IMPORTANT : pistes heuristiques non confirmées, pas de noms ou téléphones publiés.")


if __name__=="__main__":
    main()
    audit_qualified()
