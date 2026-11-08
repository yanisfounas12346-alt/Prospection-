# France Clim — formulaire entrant de rappel (département 13)

Cette application web crée une **source directe de demandes de particuliers**, plutôt que d'inventer des contacts depuis des réseaux sociaux.

**Site déployé :** https://france-clim-demandes-13.vercel.app — projet Vercel `france-clim-demandes-13`, équipe `yanisfounas12346-1342`.
Le déploiement de production a réussi et les **10 tests Node ont passé sur Vercel (10/10)** le 8 novembre 2026. Les variables d'environnement sont actuellement absentes : **le formulaire est affiché mais les envois sont désactivés (fail-closed)**.
La protection SSO Vercel a été désactivée uniquement pour ce projet afin de pouvoir recevoir des visiteurs sans compte.
Les fichiers ont été déployés directement : **les commits GitHub ultérieurs ne sont pas encore automatiquement synchronisés sur Vercel** ; reconnecter GitHub ou déployer explicitement les nouvelles versions.



## Ce qui est livré
- Formulaire français pour climatisation/chauffe-eau, 13xxx, nom, téléphone, commune et besoin.
- Case non précochée de demande expresse de rappel, horodatage serveur UTC, source et preuve dans l'e-mail.
- Protection Cloudflare Turnstile vérifiée côté serveur, piège antispam, validation stricte et absence de coordonnées dans la réponse publique.
- Envoi au professionnel via Resend (domaine expéditeur vérifié) ; réponse de succès uniquement sur accusé d'acceptation de l'API e-mail.
- Envoi refusé lorsque le canal n'est pas configuré. Aucun secret dans le dépôt.
- Tests de non-régression : `cd prospects && npm test`.

## Déploiement Vercel
Le projet Vercel est **déjà créé** et une version a été déployée depuis les fichiers du sous-dossier `prospects`, sans liaison Git automatique.

Avant d'activer le formulaire, renseigner **Project Settings → Environment Variables**, cibler **Production** et redéployer le site pour que les fonctions reçoivent les nouvelles variables. Si tu veux déployer automatiquement les futures versions depuis GitHub, autoriser la liaison GitHub dans Vercel puis lier le dépôt en choisissant `prospects` comme répertoire racine.

Configurer ces **variables d'environnement secrètes** dans Vercel (Production et Preview selon besoin) :

| Variable | Utilité |
| --- | --- |
| `TURNSTILE_SITE_KEY` | Clé publique du widget Cloudflare Turnstile (visible depuis `/api/config`) |
| `TURNSTILE_SECRET` | Secret Turnstile (ne jamais publier) |
| `TURNSTILE_EXPECTED_HOSTNAME` | Nom d'hôte exact sur lequel le formulaire est utilisé, sans `https://`, par exemple `france-clim-demandes-13.vercel.app` ; configurez également cet hôte comme autorisé dans Turnstile. |
| `TURNSTILE_EXPECTED_HOSTNAME` | Nom d'hôte exact validé par Cloudflare, par exemple `france-clim-demandes-13.vercel.app` (sans `https://`) |
| `RESEND_API_KEY` | Clé API Resend |
| `LEAD_FROM_EMAIL` | Adresse d'expédition sous un domaine vérifié, par exemple `Demandes <demandes@domaine-verifie.fr>` |
| `LEAD_TO_EMAIL` | Adresse privée du professionnel qui recevra les demandes |

Dans Cloudflare Turnstile, autoriser le domaine réellement utilisé. Le captcha émet l'action `demande_rappel`, que le serveur valide **avec le hostname exact**. Les tests refusent les jetons d'autres sites.
Le serveur refuse les demandes mal formées ou trop volumineuses, vérifie l'accord de rappel, et associe une clé d'idempotence à l'envoi e-mail pour limiter les doublons après un incident réseau.

**Avant activation réelle**, compléter les mentions légales et l'identité/contact du responsable du traitement, fixer la durée de conservation des e-mails, et vérifier la conformité des sous-traitants RGPD. Une adresse d'envoi Resend doit relever d'un domaine vérifié.

L'envoi utilise l'API transactionnelle Resend, pas une extraction de contacts de plateformes.
Le formulaire affiche « bientôt disponible » si un secret manque.

## État du déploiement Vercel (8 novembre 2026)

Un projet de production `france-clim-demandes-13` a été créé :
`https://france-clim-demandes-13.vercel.app`.

Au dernier contrôle, le déploiement était **READY** mais **aucune variable d'environnement** n'était configurée : le formulaire est donc volontairement désactivé. Le statut READY indique seulement que les fichiers sont déployés, pas que les prospects peuvent déjà envoyer leurs demandes.

Pour activer la réception : dans Vercel, ouvrir **france-clim-demandes-13 → Settings → Environment Variables**, ajouter les six variables ci-dessus à l'environnement **Production** et redéployer le projet. Les cinq valeurs privées restent exclusivement dans Vercel, jamais dans GitHub. `TURNSTILE_SITE_KEY` est la seule valeur publique. Configurer Turnstile pour le nom d'hôte final, y compris si vous basculez vers un domaine personnalisé.

Vérifier ensuite `https://france-clim-demandes-13.vercel.app/api/config` : `enabled` doit être `true` avant les essais, sans jamais y exposer de clé privée. Le formulaire doit recevoir un essai autorisé avec un numéro de test et un e-mail confirmé avant diffusion. **Ne jamais publier de demandes nominatives ou de téléphones dans le dépôt public.**

## Test réel avant diffusion
1. Vérifier Turnstile sur le **vrai domaine** utilisé, y compris le domaine Vercel si nécessaire.
2. Envoyer un formulaire fictif avec un numéro de test autorisé. Vérifier qu'une seule notification arrive dans la boîte professionnelle.
3. Simuler un refus Turnstile et une erreur Resend : aucune confirmation de succès ne doit apparaître.
4. Vérifier la politique de confidentialité, la durée de conservation dans la boîte e-mail, les accès et la suppression des messages.
5. Publier le lien sur les canaux de prospection **autorisés** : site web de l'artisan, fiche établissement, QR code, signatures de messages et partenaires.

## Limites importantes
- La mise en ligne **n'apporte aucun visiteur à elle seule** : il faut le publier et, au besoin, investir en visibilité.
- Les e-mails sont transmis par un prestataire ; vérifier ses conditions de traitement de données et les obligations RGPD.
- L'e-mail de demande arrive dans la boîte du professionnel, **pas directement dans l'APK Android**. La synchronisation privée reste un autre chantier.
- La délivrance e-mail reste à contrôler : un identifiant Resend confirme l'acceptation, pas la lecture du message.
- Le dispositif n'appelle jamais automatiquement un particulier. Un formulaire public sans case de rappel cochée est rejeté.
- Ne pas ajouter de données personnelles de prospects au dépôt GitHub public.
