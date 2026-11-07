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

if __name__=="__main__":
    main()
