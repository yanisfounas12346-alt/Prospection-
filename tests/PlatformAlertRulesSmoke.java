package fr.franceclim.pro;

public final class PlatformAlertRulesSmoke {
  private static void check(boolean condition,String text){
    if(!condition)throw new AssertionError(text);
    System.out.println("PASS : "+text);
  }
  public static void main(String[] args){
    check(PlatformAlertRules.accept("fr.exemple.travaux",
      "Nouvelle demande dans votre secteur",
      "Remplacement d'un chauffe-eau à Marseille"),
      "demande plateforme professionnelle");
    check(PlatformAlertRules.accept("fr.exemple.travaux",
      "Nouvelle mission", "Installer une clim à Vitrolles"),
      "notification de mission");
    check(PlatformAlertRules.accept("fr.leboncoin",
      "Nouvelle annonce correspondant à votre alerte",
      "Chauffe-eau Marseille"),
      "alerte annonce Leboncoin");
    check(PlatformAlertRules.accept("com.facebook.katana",
      "Julie a publié dans le groupe du 13",
      "Je cherche une entreprise pour changer ma clim"),
      "publication d'un groupe autorisé");
    check(!PlatformAlertRules.accept("com.facebook.katana",
      "Nouveau message privé",
      "Remplacement chauffe-eau à Marseille"),
      "message privé Facebook bloqué");
    check(!PlatformAlertRules.accept("fr.exemple.travaux",
      "Code de connexion", "Votre code est 123456"),
      "authentification bloquée");
    check(!PlatformAlertRules.accept("fr.exemple.travaux",
      "Nouveau message", "Vous avez une nouvelle conversation"),
      "messagerie bloquée");
    check(!PlatformAlertRules.accept("fr.exemple.travaux",
      "Facture de votre abonnement", "Paiement renouvellement"),
      "facture bloquée");
  }
}
