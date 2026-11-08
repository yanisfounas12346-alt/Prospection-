# France Clim Radar 13 — plan de livraison V3

Dernier audit technique : 8 novembre 2026. Pas de promesse de couverture à 100 % de plateformes externes.

## Corrigé dans le code V2.6 (validation sur appareil requise)
- Dossiers CRM locaux chiffrés AES-GCM avec clé Android Keystore ; migration de la valeur en clair après succès de l'écriture chiffrée.
- Import CSV : différenciation de clients avec demandes identiques et rattachement des coordonnées via empreinte stable.
- Le scanner RSS ne marque pas une annonce traitée lorsque son stockage échoue.
- Les erreurs de stockage Bluesky sont comptées.
- Pas de suppression silencieuse des anciennes pistes : limite explicite de 500 dossiers avant sauvegarde et gestion des dossiers.
- Contrôles statiques de confidentialité intégrés au workflow APK, versionCode porté à 8.

## Non-régression ajoutée (8 novembre 2026)
- Sept tests Python dans `radar13/test_delivery.py` : reprises RSS/Bluesky si WhatsApp absent, e-mails non acquittés si notification impossible, preuve de message ID du fournisseur, reprise après échec et conservation des dernières empreintes.
- Les empreintes d'annonces qualifiées ne sont désormais enregistrées comme traitées qu'après confirmation d'envoi sur le canal actif. Sans ce canal, une demande reste en attente tant qu'elle figure dans la source.
- Les publications Bluesky communes à plusieurs recherches sont dédoublonnées pendant chaque analyse.
- La mémoire anti-doublons préserve les dernières empreintes insérées et ne sélectionne plus arbitrairement les valeurs SHA.
- Ces tests sont appelés par les workflows GitHub Actions Android et veille. La réussite des tests sur le dernier commit doit être confirmée dans GitHub Actions ; elle n'est pas prouvée par le seul dépôt du code.
- **Limite non résolue :** il n'existe pas encore de boîte d'attente persistante contenant des demandes qualifiées côté serveur sécurisé. Une publication devenue inaccessible peut donc être perdue si aucun canal d'alerte n'était disponible.

## À valider impérativement avant diffusion
1. GitHub Actions « build-apk » : tests Java et Python, contrôle carte, contrôle de confidentialité, compilation Gradle, signature et artefact APK.
2. Essai sur Android réel : mise à jour sans perte des contacts, migration depuis SharedPreferences anciennes, redémarrage, refus d'accès aux notifications, absence de réseau, retour en ligne, export et réimport JSON.
3. Mesurer les faux positifs et les demandes manquées sur un corpus de demandes réellement annotées (sans mettre les données personnelles dans GitHub).
4. Vérifier les permissions, la politique de conservation et l'effacement des données en conditions réelles.

## Bloquants fonctionnels pour le radar commercial autonome
- Aucune synchronisation des prospects GitHub Actions vers l'APK : prévoir une API HTTPS privée, authentification, base chiffrée et journalisation des synchronisations. Ne jamais stocker des contacts dans un dépôt GitHub public.
- Pas d'accès autorisé aux demandes de devis et coordonnées des plateformes partenaires : l'intégration nécessite un accord/API/compte professionnel spécifique.
- Les workflows GitHub cron et les jobs Android peuvent être retardés ou manqués : supervision externe et alertes d'indisponibilité nécessaires pour un objectif 24 h/24.
- Un numéro disponible publiquement n'est pas une preuve de demande de rappel. Enregistrer l'origine, la date, l'objet, la preuve et l'opposition éventuelle, et ne permettre un contact que dans le cadre légal.
- Les sauvegardes JSON sont exportées en clair : envisager une exportation chiffrée avec protection par mot de passe et récupération contrôlée.

## Critères de réussite commerciale
- Nombre de sources actives, latence entre publication et détection, % d'erreurs, annonces à vérifier et faux négatifs mesurés.
- Nombre de demandes entrantes confirmées, dossiers avec rappel explicitement demandé, rendez-vous obtenus, chantiers signés.
- Un cas d'essai en chaîne : formulaire réel accepté -> arrivée API privée -> chiffrement -> synchronisation -> fiche locale -> notification -> rappel documenté.

**Statut : V2.6 corrections déposées, V3 acquisition et synchronisation non livrées.**
