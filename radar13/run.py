"""Veille continue optionnelle (GitHub Actions) sur RSS et alertes e-mail autorisées.

Seuls les empreintes SHA-256 sont conservées dans le dépôt public.
Aucune prise de contact automatique avec les particuliers.
"""
from __future__ import annotations

import argparse
import email
from email import policy
from email.header import decode_header, make_header
import hashlib
import html
import imaplib
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlparse, urlunparse
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

from .engine import classify

ROOT = Path(__file__).resolve().parent.parent
STATE_FILE = ROOT / "data" / "seen-hashes.json"
SOURCES_FILE = ROOT / "config" / "sources.json"


def fingerprint(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def public_link(value):
    """Ne pas transmettre des paramètres privés / tokens présents dans les URLs."""
    try:
        parts = urlparse(value.strip())
        if parts.scheme != "https" or not parts.hostname:
            return ""
        return urlunparse(("https", parts.hostname, parts.path, "", "", ""))
    except ValueError:
        return ""


def plain(value):
    value = re.sub(r"(?is)<(script|style).*?</\\1>", " ", value or "")
    value = re.sub(r"<[^>]+>", " ", value)
    value = html.unescape(value)
    value = re.sub(r"(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b", "[e-mail masqué]", value)
    value = re.sub(r"(?<!\\d)(?:\\+33|0)[1-9](?:[ .-]?\\d{2}){4}(?!\\d)", "[téléphone masqué]", value)
    return re.sub(r"\\s+", " ", value).strip()


def parse_feed(raw):
    """RSS 2.0 et Atom, sans exécution de script ni extraction HTML."""
    tree = ET.fromstring(raw)
    items = []
    for el in tree.iter():
        tag = el.tag.rsplit("}", 1)[-1].lower()
        if tag not in ("item", "entry"):
            continue
        children = {child.tag.rsplit("}", 1)[-1].lower(): child for child in el}
        def get(*names):
            for name in names:
                found = children.get(name)
                if found is not None:
                    return "".join(found.itertext()).strip()
            return ""
        title = get("title")
        summary = get("description", "summary", "content")
        link_el = children.get("link")
        link = (link_el.get("href") or "") if link_el is not None else ""
        if not link:
            link = get("link")
        ident = get("guid", "id") or link or title
        items.append({"id": ident, "title": title, "text": plain(title + " " + summary),
                      "link": public_link(link)})
    return items[:150]


def load_feeds():
    if not SOURCES_FILE.exists():
        return []
    values = json.loads(SOURCES_FILE.read_text(encoding="utf-8"))
    if not isinstance(values, list):
        raise ValueError("config/sources.json doit contenir une liste de flux")
    feeds = []
    for entry in values[:30]:
        if not isinstance(entry, dict) or not entry.get("enabled", True):
            continue
        url = entry.get("url", "")
        if isinstance(url, str) and url.startswith("https://"):
            feeds.append((str(entry.get("name") or "Flux RSS"), url))
    extra = os.environ.get("RADAR_RSS_FEEDS", "")
    feeds += [("Flux RSS privé", u.strip()) for u in extra.splitlines()
              if u.strip().startswith("https://")]
    return feeds[:30]


def fetch_feed(url):
    req = Request(url, headers={"User-Agent": "FranceClimRadar/2.0 RSS-monitor"})
    with urlopen(req, timeout=20) as response:
        if response.status != 200:
            raise ValueError(f"HTTP {response.status}")
        raw = response.read(1_000_001)
        if len(raw) > 1_000_000:
            raise ValueError("Flux dépassant 1 Mo")
        return parse_feed(raw)


def load_state():
    if not STATE_FILE.exists():
        return {"feeds": {}}
    data = json.loads(STATE_FILE.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or not isinstance(data.get("feeds"), dict):
        return {"feeds": {}}
    return data


def save_state(data):
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                          encoding="utf-8")


def telegram_send(text):
    token = os.environ.get("RADAR_TELEGRAM_TOKEN", "")
    chat_id = os.environ.get("RADAR_TELEGRAM_CHAT_ID", "")
    if not token or not chat_id:
        raise RuntimeError("Télégram non configuré")
    import urllib.parse
    body = urllib.parse.urlencode({"chat_id": chat_id, "text": text}).encode()
    with urlopen(Request(f"https://api.telegram.org/bot{token}/sendMessage",
                         data=body, method="POST"), timeout=15) as res:
        if res.status != 200:
            raise RuntimeError("Notification Telegram refusée")


def alert(item, source):
    result = classify(item["text"])
    if not result or result["score"] < 65:
        return False
    text = (f"France Clim • Publication à vérifier\\n"
            f"{result['category']} — {result['city']}\\n"
            f"Priorité : {result['score']}/100\\n"
            f"Source : {source}\\n"
            f"{plain(item['text'])[:170]}\\n"
            f"{item.get('link','')}")
    telegram_send(text[:750])
    return True


def scan_feeds(state):
    fresh = 0
    reported = 0
    for source, url in load_feeds():
        key = fingerprint(url)
        old = state["feeds"].get(key)
        first = old is None
        seen = set(old or [])
        try:
            items = fetch_feed(url)
        except Exception as exc:
            print(f"Flux inaccessible ({source}) : {type(exc).__name__}", file=sys.stderr)
            continue
        # Ordre inverse pour annoncer d'abord les nouveautés les plus anciennes.
        for item in reversed(items):
            ident = fingerprint(item["id"] or item["text"])
            if ident in seen:
                continue
            # Initialisation prudente : les posts historiques ne sont pas envoyés.
            if not first and reported < 12:
                if alert(item, source):
                    reported += 1
            seen.add(ident)
            fresh += 1
        state["feeds"][key] = sorted(seen)[-4000:]
        print(f"{source} : {len(items)} publication(s) lues")
    return fresh, reported


def alert_mailbox():
    host = os.environ.get("RADAR_IMAP_HOST", "")
    user = os.environ.get("RADAR_IMAP_USER", "")
    password = os.environ.get("RADAR_IMAP_PASSWORD", "")
    folder = os.environ.get("RADAR_IMAP_FOLDER", "FranceClimRadar")
    if not (host and user and password):
        return 0
    result = 0
    with imaplib.IMAP4_SSL(host, timeout=20) as client:
        client.login(user, password)
        ok, _ = client.select(folder)
        if ok != "OK":
            raise ValueError("Dossier IMAP introuvable : vérifier RADAR_IMAP_FOLDER")
        ok, values = client.search(None, "UNSEEN")
        if ok != "OK":
            raise ValueError("Recherche IMAP impossible")
        ids = (values[0] or b"").split()[-40:]
        for uid in ids:
            status, parts = client.fetch(uid, "(RFC822)")
            if status != "OK":
                continue
            raw = next((part[1] for part in parts if isinstance(part, tuple)), None)
            if raw is None:
                continue
            msg = email.message_from_bytes(raw, policy=policy.default)
            subject = str(make_header(decode_header(msg.get("Subject", ""))))
            body = msg.get_body(preferencelist=("plain", "html"))
            text = plain(subject+" "+(body.get_content() if body else ""))
            link_match = re.search(r"https://[^\\s<>\\\"']+", text)
            url = public_link(link_match.group(0)) if link_match else ""
            item = {"id": fingerprint(subject + text), "text": text, "link": url}
            if alert(item, "Alerte e-mail officielle"):
                result += 1
            # Marquer lu seulement après traitement dans le dossier dédié.
            client.store(uid, "+FLAGS", "\\Seen")
    return result


def demo():
    raw = (ROOT / "fixtures" / "demo.rss").read_bytes()
    matches = []
    for item in parse_feed(raw):
        result = classify(item["text"])
        if result:
            matches.append(result)
            print(f"DEMO : {result['category']} / {result['city']} / {result['score']}")
    print(f"{len(matches)} demande(s) qualifiée(s) fictive(s)")
    return 0 if len(matches) == 2 else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--demo", action="store_true", help="Tester hors ligne avec des annonces fictives")
    args = parser.parse_args()
    if args.demo:
        return demo()
    if not os.environ.get("RADAR_TELEGRAM_TOKEN") or not os.environ.get("RADAR_TELEGRAM_CHAT_ID"):
        print("Alerte non configurée : ajouter RADAR_TELEGRAM_TOKEN et RADAR_TELEGRAM_CHAT_ID dans Secrets.")
        return 0
    state = load_state()
    try:
        fresh, sent = scan_feeds(state)
        email_sent = alert_mailbox()
    except Exception as exc:
        print(f"Radar interrompu : {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1
    save_state(state)
    print(f"Veille : {fresh} nouveaux éléments, {sent} alertes RSS, {email_sent} alertes IMAP.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
