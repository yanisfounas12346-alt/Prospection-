"""Notifications d'opportunités internes via l'API officielle WhatsApp Business.

Envoie uniquement au numéro WhatsApp de l'artisan ayant accepté les alertes.
Aucun message n'est envoyé aux auteurs des annonces.
Les modèles sortants doivent être approuvés dans WhatsApp Manager.
"""
from __future__ import annotations
import json
import os
import re
from urllib.request import Request, urlopen

def configured(environ=None):
    env = os.environ if environ is None else environ
    keys = ("RADAR_WHATSAPP_ACCESS_TOKEN", "RADAR_WHATSAPP_PHONE_NUMBER_ID",
            "RADAR_WHATSAPP_RECIPIENT", "RADAR_WHATSAPP_TEMPLATE_NAME")
    return all(env.get(key, "").strip() for key in keys)

def template_payload(category, city, score, source, environ=None):
    """Le modèle approuvé contient exactement 4 paramètres texte dans le corps."""
    env = os.environ if environ is None else environ
    recipient = re.sub(r"[^0-9]", "", env.get("RADAR_WHATSAPP_RECIPIENT", ""))
    template = env.get("RADAR_WHATSAPP_TEMPLATE_NAME", "").strip()
    language = env.get("RADAR_WHATSAPP_TEMPLATE_LANG", "fr").strip() or "fr"
    if not 9 <= len(recipient) <= 15:
        raise ValueError("Numéro WhatsApp du destinataire invalide")
    if not re.fullmatch(r"[a-z0-9_]{3,512}", template):
        raise ValueError("Nom de modèle WhatsApp invalide")
    values = (category, city, str(score), source)
    clean = [re.sub(r"\s+", " ", str(item)).strip()[:80] or "Non précisé" for item in values]
    return {
        "messaging_product": "whatsapp", "to": recipient, "type": "template",
        "template": {
            "name": template, "language": {"code": language},
            "components": [{
                "type": "body",
                "parameters": [{"type": "text", "text": item} for item in clean]
            }],
        },
    }

def send_template(category, city, score, source):
    """Un message de notification à l'artisan (pas de démarchage)."""
    if not configured():
        raise RuntimeError("WhatsApp Business Cloud API non configurée")
    token = os.environ["RADAR_WHATSAPP_ACCESS_TOKEN"].strip()
    phone_id = os.environ["RADAR_WHATSAPP_PHONE_NUMBER_ID"].strip()
    version = os.environ.get("RADAR_WHATSAPP_GRAPH_VERSION") or "v26.0"
    if not re.fullmatch(r"[0-9]{5,30}", phone_id):
        raise ValueError("Identifiant du numéro émetteur invalide")
    if not re.fullmatch(r"v[0-9]{1,3}\.[0-9]", version):
        raise ValueError("Version Graph API invalide")
    body = json.dumps(template_payload(category, city, score, source)).encode("utf-8")
    url = f"https://graph.facebook.com/{version}/{phone_id}/messages"
    req = Request(url, data=body, method="POST", headers={
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json"
    })
    try:
        with urlopen(req, timeout=20) as response:
            if not (200 <= response.status < 300):
                raise RuntimeError("WhatsApp Business a refusé le message")
            return json.load(response).get("messages", [{}])[0].get("id")
    except Exception as exc:
        # Ne jamais journaliser la réponse brute ni le jeton d'API.
        raise RuntimeError("Échec d'envoi WhatsApp Business : vérifier les permissions, "
                           "le destinataire et l'approbation du modèle") from None
