"""Qualification prudente des publications de particuliers (sans prise de contact)."""
import re
import unicodedata

CITIES = ["Aix-en-Provence","Allauch","Alleins","Arles","Aubagne","Aureille","Auriol","Aurons","La Barben","Barbentane","Les Baux-de-Provence","Beaurecueil","Belcodène","Berre-l'Étang","Bouc-Bel-Air","La Bouilladisse","Boulbon","Cabannes","Cabriès","Cadolive","Carnoux-en-Provence","Carry-le-Rouet","Cassis","Ceyreste","Charleval","Châteauneuf-le-Rouge","Châteauneuf-les-Martigues","Châteaurenard","La Ciotat","Cornillon-Confoux","Coudoux","Cuges-les-Pins","La Destrousse","Ensuès-la-Redonne","Eygalières","Eyguières","Eyragues","La Fare-les-Oliviers","Fontvieille","Fos-sur-Mer","Fuveau","Gardanne","Gignac-la-Nerthe","Grans","Graveson","Gréasque","Gémenos","Istres","Jouques","Lamanon","Lambesc","Lançon-Provence","Maillane","Mallemort","Marignane","Marseille","Martigues","Mas-Blanc-des-Alpilles","Maussane-les-Alpilles","Meyrargues","Meyreuil","Mimet","Miramas","Mollégès","Mouriès","Noves","Orgon","Paradou","La Penne-sur-Huveaune","Les Pennes-Mirabeau","Peynier","Peypin","Peyrolles-en-Provence","Plan-d'Orgon","Plan-de-Cuques","Port-Saint-Louis-du-Rhône","Port-de-Bouc","Le Puy-Sainte-Réparade","Puyloubier","Pélissanne","Rognac","Rognes","Rognonas","La Roque-d'Anthéron","Roquefort-la-Bédoule","Roquevaire","Rousset","Le Rove","Saint-Andiol","Saint-Antonin-sur-Bayon","Saint-Cannat","Saint-Chamas","Saint-Estève-Janson","Saint-Marc-Jaumegarde","Saint-Martin-de-Crau","Saint-Mitre-les-Remparts","Saint-Paul-lès-Durance","Saint-Pierre-de-Mézoargues","Saint-Rémy-de-Provence","Saint-Savournin","Saint-Victoret","Saint-Étienne-du-Grès","Saintes-Maries-de-la-Mer","Salon-de-Provence","Sausset-les-Pins","Septèmes-les-Vallons","Simiane-Collongue","Sénas","Tarascon","Le Tholonet","Trets","Vauvenargues","Velaux","Venelles","Ventabren","Vernègues","Verquières","Vitrolles","Éguilles"]
POSTAL_13 = re.compile(r"(?<!\d)13\d{3}(?!\d)")

def norm(text):
    value = unicodedata.normalize("NFKD", str(text or "").casefold())
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    return " " + re.sub(r"[^a-z0-9]+", " ", value).strip() + " "

def has(text, *phrases):
    return any(norm(phrase) in text for phrase in phrases)

def classify(text):
    """Retourne catégorie, ville et score, ou None. La qualification reste à vérifier."""
    body = norm(text)
    if len(body) < 22:
        return None
    heat = has(body, "chauffe eau", "cumulus", "ballon d eau chaude",
               "ballon electrique", "plus d eau chaude", "ballon thermodynamique")
    cool = has(body, "climatisation", "climatiseur", "clim", "multi split",
               "monosplit", "gainable", "pac air air", "pompe a chaleur air air")
    if not (heat or cool):
        return None
    if has(body, "je vends", "a vendre", "vend mon", "vente de", "occasion",
           "je propose mes services", "nous proposons", "artisan qualifie",
           "entreprise specialisee", "contactez nous", "nos prestations",
           "tarif installation", "promo installation", "recrutons"):
        return None
    if has(body, "entretien annuel", "nettoyage filtre", "recharge de gaz",
           "recherche de fuite uniquement", "reparation uniquement"):
        return None
    explicit = has(body, "je cherche", "je recherche", "cherche un",
                   "recherche un", "besoin d un", "besoin d une", "besoin de",
                   "quelqu un connait", "vous connaissez", "devis pour",
                   "faire installer", "faire poser", "faire remplacer", "faire changer",
                   "qui peut installer", "qui peut remplacer", "trouver un installateur",
                   "trouver un plombier", "j aimerais installer", "je voudrais installer",
                   "je souhaite installer", "je veux remplacer")
    urgent = has(body, "plus d eau chaude", "en panne", "hs", "perce",
                 "fuit", "fuite", "urgent", "urgence", "ne fonctionne plus",
                 "ne chauffe plus", "vient de lacher", "a remplacer rapidement")
    personal = has(body, "mon chauffe eau", "mon cumulus", "mon ballon", "ma clim",
                   "chez moi", "ma maison", "mon appartement", "notre maison")
    if not (explicit or (urgent and personal)):
        return None
    city = ""
    for name in CITIES:
        if norm(name) in body and len(name) > len(city):
            city = name
    if not city:
        postal = POSTAL_13.search(body)
        if postal:
            city = postal.group()
    if not city and has(body, "bouches du rhone", "departement 13", "dans le 13"):
        city = "Bouches-du-Rhône"
    if not city:
        return None
    score = min(100, 50 + 17*explicit + 18*urgent + 8*personal +
                (7 if city != "Bouches-du-Rhône" else 0))
    return {"category": "Chauffe-eau" if heat else "Climatisation",
            "city": city, "score": score,
            "urgency": bool(urgent), "requires_review": True}
