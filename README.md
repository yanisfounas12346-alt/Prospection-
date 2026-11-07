# France Clim Pro Radar 2.2 — vrais flux publics, carte, contacts communiqués

**Nouveauté 2.2 :** recherche automatique des publications publiques Bluesky via API officielle et surveillance des deux flux RSS publics Mastodon (#climatisation et #chauffeeau), vérifiés HTTP 200 le 7 novembre 2026. Sources activées par défaut dans l'application. Ce sont de vraies publications, mais elles ne sont pas forcément des demandes de particuliers dans le 13. Aucune liste de contacts ni numéro personnel ne peut être obtenu automatiquement à partir d'un profil privé.

**Nouveauté 2.1 :** carte interactive des opportunités par commune du 13, fiches avec **nom et téléphone lorsqu'ils ont été communiqués**, import CSV de demandes reçues directement, dates de visite, sauvegarde et suppression locales. Partage manuel vers WhatsApp. Aucun numéro caché ni nom privé n'est récupéré.

➡️ [Sources et carte : guide pratique](docs/SOURCES_CARTE_CONTACTS.md)

➡️ [Configurer le radar et WhatsApp Business](docs/MISE_EN_SERVICE_RADAR.md)

## Avant de télécharger

Installer la **dernière APK verte** (GitHub Actions ou la version bêta GitHub Releases). Si Android refuse de mettre à jour une ancienne bêta, **sauvegarder d'abord ses dossiers en JSON**, puis désinstaller l'ancienne application ; des APK de test signées avec des clés de debug différentes ne peuvent pas forcément se mettre à jour en place.

**Attention :** le projet ne contient pas encore de compte autorisé donnant accès à toutes les annonces de particuliers ; il faut configurer les notifications, flux RSS et formulaires réels. **Les numéros ne sont pas garantis** et ne doivent servir qu'à répondre à une demande explicite dans un cadre légal.

## Historique de la version initiale

Application Android native pour ne pas manquer les **demandes de particuliers** concernant :
- pose ou remplacement de **climatisation** (monosplit, multisplit, gainable) ;
- pose ou remplacement de **chauffe-eau** (cumulus, ballon électrique et thermodynamique).

Zone : **119 communes des Bouches-du-Rhône**, d'après l'INSEE (COG 2026).

## Ce qui fonctionne vraiment

1. **Veille automatique sur le téléphone**, après autorisation explicite d'accès aux notifications Android :
   - Leboncoin : alertes des recherches que vous avez **vous-même sauvegardées**, actives par défaut dans le radar ;
   - Facebook : **notifications de groupes**, option désactivée par défaut, à activer volontairement ;
   - pas de collecte des notifications d'autres applications, pas de lecture des messages privés Facebook ;
   - les notifications sont traitées uniquement si Android les délivre et si leur texte est visible.
2. **Import par le menu Partager** : depuis votre navigateur ou une application, partager une publication texte vers France Clim Pro.
3. **Qualification locale**, sans abonnement à une API IA :
   - type de chantier, commune ou code postal du 13, intention de chercher un artisan ;
   - priorité / à vérifier / exclusion des ventes et publicités d'artisans ;
   - urgence chauffe-eau, dédoublonnage et conservation d'un lien s'il figure dans le texte ;
   - note sur 100 **heuristique**, pas une probabilité de signature.
4. **Notifications haute priorité** uniquement si la demande paraît explicite, dans le 13.
5. **CRM local** : classement des opportunités, proposition de visite gratuite, RDV, suivi de chantier et bouton de partage manuel sur WhatsApp.

**Aucun appel, SMS, message Facebook ou e-mail n'est envoyé automatiquement à un particulier.**
Les brouillons de réponse ne doivent être utilisés que si le canal et le cadre légal permettent une réponse à la demande.

## Activer le radar

1. Installer l'APK, puis ouvrir **France Clim Pro Radar**.
2. Autoriser les notifications de l'application si Android le demande.
3. Appuyer sur **Activer l'accès aux notifications**. Android explique que ce droit est large : le radar n'extrait et n'enregistre que les contenus des applications sélectionnées contenant des termes liés à nos deux métiers.
4. Sur Leboncoin, **sauvegarder des recherches** sur le département 13 et activer les notifications. Tester plusieurs formulations : `recherche installateur clim`, `cherche pose clim`, `remplacement chauffe-eau`, `cumulus HS`. Vérifier si des demandes de particuliers sont réellement publiées dans les catégories accessibles.
5. Optionnel : sur Facebook, activer les notifications des groupes locaux autorisés, puis activer l'interrupteur Facebook dans le radar. Certaines publications peuvent être tronquées ou ne pas apparaître dans les notifications.
6. Appuyer sur **Tester avec une demande fictive**. Une opportunité de démonstration apparaît ; elle n'est pas un client réel.
7. Pour importer une publication manuellement : **Partager → France Clim Pro Radar**, puis **Analyser et ajouter**.

## Deux autres sources de veille (facultatives)

- **Flux RSS/Atom autorisés** : dans l'application, ouvrir **Configurer les sources d'annonces RSS** et ajouter une URL HTTPS par ligne. Choisir ensuite **Scanner les sources maintenant**. Android demandera des vérifications périodiques, mais peut les retarder selon l'état de la batterie. Le même CRM reçoit les publications RSS et les notifications des applications sélectionnées.
- **Veille Python côté GitHub Actions** : le workflow `radar.yml` peut vérifier des flux RSS et un dossier IMAP d'alertes officielles, même quand le téléphone dort. Les alertes WhatsApp automatiques nécessitent l'API officielle WhatsApp Business, un modèle approuvé et des secrets configurés dans GitHub. Aucun jeton ni numéro privé n'est enregistré dans le dépôt.

Pour les étapes exactes : [Guide de mise en service](docs/MISE_EN_SERVICE_RADAR.md).

**La surveillance ne crée pas de prospects** : les annonces ne seront détectées que si les sources publient réellement des demandes de particuliers exploitables.

## Limites importantes

- **Pas de scraping automatique**, ni d'accès aux plateformes sans leur permission.
- Une recherche sauvegardée Leboncoin ne garantit **pas** de retrouver les particuliers recherchant un artisan : une grande partie du site est constituée de ventes.
- Le radar **ne détecte pas les publications qu'Android ne notifie pas** (contenus de groupe privé sans notification, annonces nouvelles sans alerte, téléphone hors ligne, notifications masquées).
- Le lien d'annonce peut manquer si la notification ne le fournit pas. Ouvrir l'application d'origine.
- Les coordonnées clients sont stockées **localement** dans les préférences de l'application. Cette version n'a **aucune synchronisation ni sauvegarde cloud**, et le stockage local n'est pas chiffré : éviter d'y conserver des données sensibles.
- Une APK de test (debug) n'est pas destinée à une distribution publique. Les signatures de deux builds GitHub peuvent différer ; cette version utilise un identifiant distinct de l'ancienne application pour éviter les conflits à l'installation.
- Il faut tester l'installation et les notifications sur un téléphone Android réel.
- Le score de qualification n'est ni une estimation de prix ni une probabilité d'obtenir un chantier.

## Compilation

La compilation s'exécute sur chaque commit de `main` via [GitHub Actions](../../actions/workflows/build-apk.yml).

Télécharger l'archive **France-Clim-Pro-RADAR-v2-APK** depuis l'onglet Artifacts d'un workflow vert, puis extraire `app-debug.apk`.

Compilation manuelle avec Java 17, Android SDK 35, Gradle 8.9 :

```bash
gradle :app:assembleDebug
```

Tests Java purs : `javac -encoding UTF-8 -d build/radar-tests app/src/main/java/fr/franceclim/pro/LeadClassifier.java tests/ClassifierSmoke.java` puis `java -cp build/radar-tests fr.franceclim.pro.ClassifierSmoke`.

## Références techniques

- API Android officielle : https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Référence open source d'architecture des notifications : https://github.com/HouJia/notification-listener (approche étudiée, code original dans ce dépôt)
- Alertes officielles Leboncoin : https://assistance.leboncoin.info/hc/fr/articles/360000592385-Comment-sauvegarder-et-g%C3%A9rer-une-recherche
- INSEE, département 13 : https://www.insee.fr/fr/metadonnees/geographie/departement/13-bouches-du-rhone
