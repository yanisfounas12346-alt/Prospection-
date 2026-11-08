# France Clim — formulaire entrant de rappel (département 13)

Cette application web crée une **source directe de demandes de particuliers**, plutôt que d'inventer des contacts depuis des réseaux sociaux. Elle n'est **pas mise en production** tant que les variables secrètes ne sont pas configurées.

## Ce qui est livré
- Formulaire français pour climatisation/chauffe-eau, 13xxx, nom, téléphone, commune et besoin.
- Case non précochée de demande expresse de rappel, horodatage serveur UTC, source et preuve dans l'e-mail.
- Protection Cloudflare Turnstile vérifiée côté serveur, piège antispam, validation stricte et absence de coordonnées dans la réponse publique.
- Envoi au professionnel via Resend (domaine expéditeur vérifié) ; réponse de succès uniquement sur accusé d'acceptation de l'API e-mail.
- Envoi refusé lorsque le canal n'est pas configuré. Aucun secret dans le dépôt.
- Tests de non-régression : `cd prospects && npm test`.

## Déploiement Vercel
Créer un projet Vercel relié au dépôt `yanisfounas12346-alt/Prospection-`, avec **Root Directory : `prospects`** et Framework : Other.

Configurer ces **variables d'environnement secrètes** dans Vercel (Production et Preview selon besoin) :

| Variable | Utilité |
| --- | --- |
| `TURNSTILE_SITE_KEY` | Clé publique du widget Cloudflare Turnstile (visible depuis `/api/config`) |
| `TURNSTILE_SECRET` | Secret Turnstile (ne jamais publier) |
| `RESEND_API_KEY` | Clé API Resend |
| `LEAD_FROM_EMAIL` | Adresse d'expédition sous un domaine vérifié, par exemple `Demandes <demandes@domaine-verifie.fr>` |
| `LEAD_TO_EMAIL` | Adresse privée du professionnel qui recevra les demandes |

L'envoi utilise l'API transactionnelle Resend, pas une extraction de contacts de plateformes.
Le formulaire affiche « bientôt disponible » si un secret manque.

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
