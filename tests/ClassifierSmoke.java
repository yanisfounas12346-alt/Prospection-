package fr.franceclim.pro;
public final class ClassifierSmoke {
  static int pass=0;
  static void expect(boolean b,String message) {
    if(!b)throw new AssertionError(message);pass++;
  }
  public static void main(String[] args) {
    expect(LeadClassifier.COMMUNES.length==119,"119 communes INSEE");
    LeadClassifier.Result a=LeadClassifier.classify("Je cherche un artisan pour installer une climatisation à Marseille 13008.","");
    expect(a.eligible&&"PRIORITAIRE".equals(a.status),"clim Marseille prioritaire");
    expect(a.score>=75,"score prioritaire");
    LeadClassifier.Result b=LeadClassifier.classify("URGENT: plus d'eau chaude, je cherche un plombier pour remplacer mon cumulus à Aix-en-Provence.","");
    expect(b.eligible&&b.urgent&&"PRIORITAIRE".equals(b.status),"cumulus urgent Aix");
    LeadClassifier.Result c=LeadClassifier.classify("Je vends une climatisation d'occasion à Martigues.","");
    expect(!c.eligible&&"EXCLU".equals(c.status),"vente refusée");
    LeadClassifier.Result d=LeadClassifier.classify("Notre entreprise intervient pour la pose de climatisation, devis gratuit sans engagement à Istres.","");
    expect(!d.eligible,"publicité artisan refusée");
    LeadClassifier.Result e=LeadClassifier.classify("J'ai besoin d'installer un chauffe-eau à Éguilles.","");
    expect(e.eligible&&"Éguilles".equals(e.city),"commune Éguilles");
    LeadClassifier.Result f=LeadClassifier.classify("Je cherche un artisan pour un chauffe-eau.","");
    expect(f.eligible&&!"PRIORITAIRE".equals(f.status),"hors zone, à vérifier");
    LeadClassifier.Result g=LeadClassifier.classify("Besoin de réparer le moteur de ma voiture à Marseille.","");
    expect(!g.eligible,"hors sujet");
    LeadClassifier.Result h=LeadClassifier.classify("Je cherche quelqu'un pour poser un monosplit à La Ciotat.","");
    expect(h.eligible&&"PRIORITAIRE".equals(h.status),"monosplit La Ciotat");
    System.out.println("OK: "+pass+" tests de qualification.");
  }
}
