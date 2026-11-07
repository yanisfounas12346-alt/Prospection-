# France Clim Radar 13 — Mise en service réelle

Le radar ne crée **aucun prospect fictif** : sans flux ou alertes autorisés, il ne découvrira aucune demande.

## 1. Sur le téléphone (APK V2)

1. Installer l'APK de la dernière compilation verte **France Clim Pro - APK Android**.
2. Autoriser les notifications.
3. Ouvrir **Configurer les sources d'annonces RSS**, puis ajouter un flux **HTTPS RSS/Atom autorisé par son éditeur**, une URL par ligne.
4. Toucher **Scanner les sources maintenant**. La première synchronisation importe des résultats existants sans alerter pour chaque ancien élément. Les nouveaux résultats pertinents produisent ensuite une notification.
5. Des vérifications sont demandées environ toutes les 15 minutes. Android peut les différer selon sa batterie et son état : ce n'est pas une garantie de notification instantanée.

### Flux fictif pour vérifier que le radar fonctionne

Copier ce flux de démonstration dans l'application :

https://raw.githubusercontent.com/yanisfounas12346-alt/Prospection-/main/fixtures/demo.rss

Deux **demandes fictives** doivent apparaître (ballon à Marseille, clim à Martigues). Ce ne sont pas de vrais clients.

## 2. Trouver de véritables demandes de particuliers, sans achat de leads

**Google Alerts** : créer des recherches du type « je cherche installateur climatisation Marseille », « remplacer chauffe-eau Istres », « cherche plombier ballon eau chaude Bouches-du-Rhône ». Choisir RSS si Google propose cette sortie, ou les notifications e-mail. Le résultat dépend de ce que Google indexe : il ne couvre pas les publications privées.

**Leboncoin** : créer plusieurs recherches sauvegardées et activer les **notifications officielles e-mail**. Leboncoin n'offre pas nécessairement de flux RSS public utilisable. Ne pas extraire automatiquement ses pages sans autorisation. L'alerte reçue par e-mail peut être partagée manuellement vers l'APK, ou traitée via le dossier IMAP dédié de la partie serveur ci-dessous.

**Groupes Facebook** : ne pas récupérer automatiquement les groupes privés. Utiliser les notifications natives et **Partager** lorsque c'est autorisé. Une publication publique ne suffit pas à autoriser un démarchage.

**Fiche Google Business de France Clim** : ajouter le lien vers un formulaire de **visite technique gratuite et sans engagement**, pour recevoir des demandes volontaires. Le formulaire doit ensuite être relié à un CRM ou à un e-mail d'alertes.

## 3. Radar Python optionnel : reste actif même si le téléphone dort

Le dépôt contient `radar13` et le workflow `.github/workflows/radar.yml` qui demande une exécution toutes les 15 minutes. GitHub Actions peut retarder ou sauter des exécutions. La surveillance est inactive tant que les sources et le canal d'alerte ne sont pas configurés.

### Notifications WhatsApp pour l'artisan (et non les prospects)

**Sans compte WhatsApp Business Platform configuré, il n'existe pas d'envoi WhatsApp automatique.** L'APK fournit tout de même un bouton **Partager l'alerte sur WhatsApp** : il ouvre l'application WhatsApp ou WhatsApp Business avec le message préparé. L'utilisateur choisit le destinataire et confirme l'envoi.

Pour envoyer automatiquement une notification à l'artisan avec le **WhatsApp Business Cloud API officiel de Meta** :

1. Configurer un compte WhatsApp Business Platform, l'application Meta, le numéro émetteur et les permissions de messagerie nécessaires. Il faut le consentement du destinataire qui recevra les alertes internes.
2. Dans **WhatsApp Manager**, faire approuver un **modèle de notification** appelé, par exemple, `france_clim_radar_alerte`, avec exactement ces 4 variables dans le corps :

   > France Clim Radar : nouvelle demande à examiner. Activité : {{1}}. Commune : {{2}}. Score : {{3}}/100. Source : {{4}}.

   Meta examine et classe le modèle selon ses propres règles ; son approbation et sa catégorie tarifaire ne sont pas garanties. L'envoi peut être facturé. Il n'est pas possible d'envoyer librement un message hors fenêtre de service de 24 heures sans modèle approuvé.
3. Dans **GitHub → Settings → Secrets and variables → Actions**, définir ces **secrets** :
   - `RADAR_WHATSAPP_ACCESS_TOKEN` : jeton d'accès Meta (jamais dans le code).
   - `RADAR_WHATSAPP_PHONE_NUMBER_ID` : identifiant du numéro émetteur Cloud API, **pas** le numéro de téléphone.
   - `RADAR_WHATSAPP_RECIPIENT` : numéro WhatsApp de l'artisan consentant au format international, par exemple `336XXXXXXXX` (exemple non réel).
4. Définir les **variables** GitHub Actions :
   - `RADAR_WHATSAPP_TEMPLATE_NAME` : nom exact du modèle approuvé, par exemple `france_clim_radar_alerte`.
   - `RADAR_WHATSAPP_TEMPLATE_LANG` : code de langue approuvé, généralement `fr`.
   - `RADAR_WHATSAPP_GRAPH_VERSION` : facultatif, par défaut `v26.0`.
5. Relancer **Actions → France Clim Radar - Veille de sources autorisees** une fois les sources configurées.

**Confidentialité :** les alertes automatiques au professionnel contiennent seulement activité, commune, score heuristique et nom de source. Aucun nom, téléphone ni texte intégral d'annonce n'est envoyé sur WhatsApp par le moteur Python. Il n'envoie **aucun message aux auteurs des annonces**.

Documentation officielle : https://www.postman.com/meta/whatsapp-business-platform/request/o65u5m5/send-message-template-text et https://whatsappbusiness.com/policy/.

### Configurer des flux RSS/Atom

- Flux publics autorisés : modifier `config/sources.json` avec, par exemple,
  `[{"name":"Demandes publiques","url":"https://exemple.org/demandes.rss","enabled":true}]`.
  L'URL est illustrative, elle ne pointe pas vers une source réelle.
- Pour une URL privée avec token, utiliser le secret `RADAR_RSS_FEEDS`, une URL HTTPS par ligne, sans publier cette URL dans le dépôt.

### Configurer des alertes e-mail

Utiliser **une boîte dédiée** ou un dossier/label IMAP qui ne contient que les notifications pertinentes, jamais toute la boîte personnelle.

Secrets GitHub : `RADAR_IMAP_HOST`, `RADAR_IMAP_USER`, `RADAR_IMAP_PASSWORD`. Variable facultative : `RADAR_IMAP_FOLDER` (par défaut `FranceClimRadar`).

Pour Gmail, des mots de passe d'application sont parfois possibles avec la validation en deux étapes, selon les paramètres du compte. Sinon, il faudra une intégration OAuth adaptée. Ne jamais transmettre un mot de passe dans ce chat.

Le moteur lit les **messages non lus du dossier désigné**, analyse la demande, notifie le propriétaire si elle est pertinente, puis marque comme lus les messages traités. Il ne contacte jamais le particulier.

### Confidentialité

Le dépôt public ne stocke que des empreintes SHA-256 dans `data/seen-hashes.json` pour limiter les doublons. Les e-mails, noms, numéros de téléphone et textes de prospects n'y sont **pas** enregistrés. La notification WhatsApp automatique ne contient que des métadonnées (activité, ville, score, source), sans coordonnées client ni texte d'annonce.

## 4. Vérifier le moteur

- `python -m unittest discover -s radar13 -p "test_*.py" -v`
- `python -m radar13.run --demo`

Le flux démo contient deux demandes fictives pertinentes et deux annonces à rejeter.

## 5. Cadre légal et limites

L'installation de systèmes de climatisation et de chauffe-eau peut relever des travaux de rénovation énergétique. La prospection non sollicitée dans ce secteur est particulièrement encadrée et souvent interdite. **Le radar n'envoie aucun SMS, appel ou message commercial aux auteurs.** Répondre uniquement à une demande explicite et par un canal autorisé, dans les conditions prévues par la loi et les plateformes.

Sources d'inspiration open source : [feedparser](https://github.com/kurtmckee/feedparser) pour les flux, [Apprise](https://github.com/caronc/apprise) pour les notifications, [changedetection.io](https://github.com/dgtlmoon/changedetection.io) pour la surveillance de pages autorisée. Aucun de ces projets ne confère le droit de scraper un site tiers.
