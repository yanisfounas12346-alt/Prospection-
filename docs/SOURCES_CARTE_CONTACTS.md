# Sources compatibles, carte et contacts — France Clim Radar 13

## Sources publiques actives automatiquement depuis la V2.2

**Vérification réelle du 7 novembre 2026 :** les trois sources ont retourné HTTP 200 depuis GitHub Actions, avec respectivement 5 résultats Bluesky, 20 publications pour Mastodon #climatisation et 4 pour Mastodon #chauffeeau. Ce résultat n'est pas un comptage de prospects dans le 13.

- **Bluesky**, API AppView officielle : `https://api.bsky.app/xrpc/app.bsky.feed.searchPosts`. Recherche publique, sans compte : « climatisation », « chauffe-eau », « cumulus », « climatiseur ». Le radar lit les publications accessibles, puis ne retient que les demandes explicites et localisées dans le département 13. Les résultats indiqués par Bluesky sont des publications, **pas des particuliers identifiés comme clients**.
- **Mastodon**, flux publics RSS : `https://mastodon.social/tags/climatisation.rss` et `https://mastodon.social/tags/chauffeeau.rss`. Ces sources ne donnent pas accès aux publications privées et leurs hashtags ne garantissent pas des besoins locaux.
- Ces trois sources sont **activées par défaut** dans le Radar 2.2. Elles peuvent être désactivées séparément dans l'interface.
- Le smartphone demande une veille périodique à Android. **Environ 15 minutes est un intervalle souhaité, pas un délai de résultat garanti**. Les services publics peuvent limiter les requêtes ou être temporairement indisponibles.

Les résultats potentiels arrivent dans le même CRM que les alertes Leboncoin, les flux RSS personnalisés et les formulaires directs. Les liens ouvrent les publications d'origine. Aucun contact automatique n'est initié.

Le radar Python exécuté sur GitHub surveille aussi ces sources lorsqu'un canal d'alerte WhatsApp Business autorisé est configuré. Sans secrets WhatsApp ni modèle de notification approuvé, **aucun WhatsApp automatique n'est envoyé**.

**Reddit n'est pas connecté** : Reddit a annoncé l'arrêt de ses flux RSS publics le 13 novembre 2026 ; cette intégration serait rapidement inutilisable.

## Ce qui est réellement connecté à la version Android

| Source | Connexion | Nom et téléphone ? |
|---|---|---|
| **Alertes des recherches Leboncoin** | Avec la fonction officielle « enregistrer une recherche » et autorisation de lecture des notifications sur Android | **Non**, sauf si la personne l'a volontairement communiqué par ailleurs |
| **Notifications de groupes Facebook** | Après activation volontaire de cette source et de l'accès aux notifications du téléphone | **Non**. Aucun message privé, profil privé ou carnet d'adresses n'est lu |
| **Flux RSS/Atom HTTPS** | Entrer une URL autorisée par l'éditeur, puis scanner. Vérifications Android demandées environ toutes les 15 min, sans garantie | Non : le flux signale la demande, puis l'artisan vérifie le lien d'origine |
| **Partager depuis une application** | Utiliser « Partager → France Clim Radar » avec un texte de demande | Peut être renseigné manuellement si communiqué directement |
| **Demandes directes (formulaires)** | Import d'un fichier CSV de demandes reçues sur le site ou le formulaire du professionnel | **Oui**, si les champs ont été fournis volontairement dans le cadre de la demande |
| **Alertes e-mail officielles** | Connecteur Python IMAP optionnel sur GitHub Actions, après configuration d'un dossier e-mail dédié | L'alerte interne via WhatsApp ne contient pas de données personnelles. Pas de synchronisation du contact vers l'APK |
| **WhatsApp Business Cloud API** | Envoi d'alertes au professionnel via un modèle Meta approuvé, après configuration | Ne collecte pas les contacts des annonces. Ne contacte jamais automatiquement les auteurs |

### Ce qui n'est PAS possible par défaut

- Accéder aux groupes Facebook privés, lire les messages privés ou récupérer un numéro caché sur un profil.
- Extraire massivement les noms et numéros des annonces, contourner les CAPTCHA, les règles ou les limites de plateforme.
- Garantir que les recherches Leboncoin, Google Alerts ou les flux publics contiennent des particuliers recherchant effectivement un artisan.
- Afficher toutes les demandes en temps réel sans identifiants/API et sans véritable source publiée.
- Considérer la seule présence d'un numéro public comme une autorisation de démarchage.

**Le logiciel ne peut jamais inventer un nom ni un numéro.** Le champ peut rester « non communiqué ».

## Import CSV de demandes volontaires

Dans l'APK : **Importer un CSV de formulaires reçus**.

En-tête accepté (séparateur `;\` ou virgule, CSV UTF-8, maximum 300 lignes par import) :

```csv
nom;telephone;ville;service;demande;rappel;preuve;source
```

La colonne `demande`, `message`, `description` ou `besoin` est obligatoire. La ville est nécessaire pour la carte. `service` précise climatisation ou chauffe-eau.

Pour le champ `rappel`, utiliser `oui` uniquement si le particulier a **explicitement demandé un rappel** pour cette prestation. La colonne `preuve` doit contenir la date et le canal de la demande ; sans preuve, le bouton d'appel reste désactivé. Importer un fichier de test : [formulaire fictif](../fixtures/formulaire-fictif.csv). Les deux demandes sont des exemples, **pas des prospects réels**.

Les noms et numéros importés sont enregistrés localement sur le téléphone, sans envoi au dépôt GitHub.

## Carte interactive

Dans l'APK : **Carte des opportunités dans le 13**.

La carte utilise Leaflet et des tuiles OpenStreetMap en affichage interactif, puis lit les **centres de communes** fournis par l'API officielle [geo.api.gouv.fr](https://geo.api.gouv.fr/). Les épingles représentent le **nombre de dossiers par ville**, et non les adresses personnelles. Pour chaque point, l'utilisateur voit le nombre d'opportunités clim / chauffe-eau.

La carte exige une connexion Internet. Seules les communes suffisamment identifiées peuvent être placées. Une commune manquante est comptée comme **non géolocalisée** au lieu d'être inventée.

La carte ne transmet à ses fournisseurs **aucun nom, numéro, texte de demande ou adresse personnelle**. Seules les requêtes nécessaires aux fonds cartographiques et aux centres publics des communes quittent le téléphone.

Le fournisseur de tuiles OpenStreetMap applique une [politique d'utilisation](https://operations.osmfoundation.org/policies/tiles/) : l'app ne télécharge aucun fond de carte en masse ou pour une utilisation hors ligne. Pour une distribution professionnelle à grande échelle, choisir un fournisseur de tuiles disposant d'une licence et d'un service adapté.

## Coordonnées, visite gratuite et devis

Une fiche comporte : type de besoin, commune, priorité heuristique, source, texte public ou transmis, nom et numéro **seulement si communiqués**, statut commercial, preuve de demande de rappel et date de visite.

Le bouton d'appel est disponible uniquement quand une **demande de rappel explicite est documentée**. Il ouvre le composeur Android et **n'appelle jamais automatiquement**.

Les étapes commerciales imposent **visite technique gratuite effectuée avant devis**. L'option « planifier une visite » permet de conserver le rendez-vous.

## Protection des données

Ne conserver les coordonnées que pour la finalité annoncée et supprimer les dossiers devenus inutiles. L'APK permet de **supprimer individuellement un dossier** et de **sauvegarder/restaurer** ses données via un fichier JSON local.

Depuis la version Android 2.6, les dossiers locaux sont chiffrés en AES-GCM avec une clé Android Keystore. La migration des anciennes données locales ne supprime la version non chiffrée qu'après une écriture chiffrée réussie. Tester la mise à jour sur un appareil réel, et sauvegarder les dossiers avant de changer de signature APK.

Le fichier de sauvegarde JSON exporté contient toujours des données personnelles **en clair**. Le conserver **dans un espace privé**, jamais dans un dépôt GitHub public, un canal WhatsApp collectif ou un service non autorisé. Le serveur GitHub de veille n'enregistre pas les noms et numéros des clients et ne synchronise pas les dossiers vers l'APK.

### Rappel réglementaire France 2026

Depuis le 11 août 2026, le démarchage téléphonique est interdit par principe sans accord préalable pour les particuliers. En matière de rénovation énergétique, le démarchage commercial est particulièrement interdit, et la réponse à une **demande explicite de rappel** est strictement limitée à son objet et à un délai réglementaire, notamment **cinq jours ouvrables**. Il faut vérifier les conditions applicables au chantier et conserver la preuve de cette demande avant tout appel.

Informations officielles : [CNIL](https://www.cnil.fr/fr/prospection-commerciale-par-telephone-hors-automate-dappel-quelles-sont-les-regles), [Service-Public](https://www.service-public.gouv.fr/particuliers/vosdroits/F33267).

## Activer les différentes sources

1. Sur Leboncoin, enregistrer plusieurs recherches ciblées dans le département 13 et activer leurs notifications **dans l'application officielle**.
2. Sur l'APK France Clim Radar, ouvrir « Choisir et connecter les sources compatibles » puis activer uniquement les canaux utiles. Accorder l'accès aux notifications Android en connaissance de cause : cette permission est large.
3. Pour les alertes web indexées, ouvrir **Google Alerts**, créer plusieurs alertes de phrases de besoins, sélectionner **RSS** si disponible et ajouter le lien dans « Configurer les sources RSS ». Un flux doit contenir des publications réellement accessibles et autorisées.
4. Importer les demandes reçues volontairement via un formulaire (CSV).
5. En option, configurer le **radar Python sur GitHub** et l'**API WhatsApp Business** suivant [la mise en service du radar](MISE_EN_SERVICE_RADAR.md). Sans secrets Meta et modèle approuvé, aucune alerte WhatsApp automatique ne part.

La première synchronisation RSS importe des résultats existants, mais aucun outil ne peut garantir un volume de vrais chantiers à cette étape.
