package fr.franceclim.pro;

/** Règles testables des notifications de demandes de travaux sélectionnées.
 * Pas d'e-mails privés, messages privés, paiements, ni données de compte.
 */
public final class PlatformAlertRules {
  private PlatformAlertRules(){}

  public static boolean accept(String pkg,String title,String body) {
    String n=" "+LeadClassifier.normalize((title==null?"":title)+" "+(body==null?"":body))+" ";
    if(n.trim().length()<12)return false;
    if(contains(n,"message prive","messagerie privee","nouveau message",
      "votre conversation","votre messagerie","conversation avec",
      "paiement","facture","abonnement","code de connexion",
      "connexion a votre compte","verification de compte","mot de passe",
      "nouveau commentaire","reponse a votre message",
      "transaction","colis","livraison","mot de passe oublie"))return false;

    if("com.facebook.katana".equals(pkg)) {
      return contains(n," publie dans "," publication dans ",
        " posted in "," dans le groupe "," groupe ");
    }
    if("fr.leboncoin".equals(pkg)) {
      return contains(n,"nouvelle annonce","annonce correspondant",
        "nouvelle recherche","alerte","demande","recherche",
        "installation","remplacement","climatisation","chauffe eau","cumulus");
    }
    return contains(n,"demande","recherche","annonce","chantier",
      "un voisin","besoin","nouvelle mission","nouveau projet",
      "sollicitation","intervention","votre secteur","votre zone",
      "nouveau client","une mission","nouvelle prestation",
      "nouvelle opportunite","projet de travaux","demande de devis");
  }

  private static boolean contains(String n,String... patterns){
    for(String p:patterns)if(n.contains(" "+LeadClassifier.normalize(p)+" "))return true;
    return false;
  }
}
