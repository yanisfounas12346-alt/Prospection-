"""Adaptateur officiel pour les publications publiques Bluesky (sans compte).

On ne collecte pas les profils privés, on n'envoie aucun message et
l'envoi des alertes au professionnel est géré séparément par run.py.
"""
from __future__ import annotations
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import json
import re

API = "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts"
QUERIES = ("climatisation", "chauffe-eau", "cumulus", "climatiseur")


def parse_search(payload):
    """Convertit la réponse AppView en publications candidates sans profil personnel."""
    if not isinstance(payload, dict):
        raise ValueError("Réponse API Bluesky invalide")
    posts = payload.get("posts", [])
    if not isinstance(posts, list):
        raise ValueError("Réponse API Bluesky non conforme")
    output = []
    for item in posts:
        if not isinstance(item, dict):
            continue
        record = item.get("record") or {}
        author = item.get("author") or {}
        if not isinstance(record, dict) or not isinstance(author, dict):
            continue
        message = record.get("text") or ""
        if not isinstance(message, str) or not 15 <= len(message) <= 1800:
            continue
        uri = item.get("uri") or ""
        handle = author.get("handle") or ""
        if not re.fullmatch(r"[A-Za-z0-9.-]{3,255}", handle):
            continue
        key = uri.rsplit("/", 1)[-1]
        if not re.fullmatch(r"[A-Za-z0-9_-]{5,80}", key):
            continue
        url = f"https://bsky.app/profile/{handle}/post/{key}"
        output.append({"id": uri, "title": "", "text": message, "link": url})
    return output


def fetch_search(query):
    if query not in QUERIES:
        raise ValueError("Mot-clé non autorisé")
    params = urlencode({"q": query, "sort": "latest", "lang": "fr", "limit": 60})
    request = Request(f"{API}?{params}", headers={
        "User-Agent": "FranceClimRadar13/2.2 (public Bluesky API search)",
        "Accept": "application/json",
    })
    with urlopen(request, timeout=20) as response:
        if response.status != 200:
            raise ValueError(f"API Bluesky HTTP {response.status}")
        raw = response.read(1_000_001)
        if len(raw) > 1_000_000:
            raise ValueError("Réponse Bluesky trop volumineuse")
        return parse_search(json.loads(raw))
