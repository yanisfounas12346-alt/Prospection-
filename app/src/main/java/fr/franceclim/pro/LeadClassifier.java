package fr.franceclim.pro;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline classification of homeowner HVAC/hot-water requests. No scraping or AI API. */
public final class LeadClassifier {
  private LeadClassifier() {}
  public static final String[] COMMUNES = (
    "Aix-en-Provence|Allauch|Alleins|Arles|Aubagne|Aureille|Auriol|Aurons|La Barben|Barbentane|" +
    "Les Baux-de-Provence|Beaurecueil|Belcodène|Berre-l'Étang|Bouc-Bel-Air|La Bouilladisse|Boulbon|" +
    "Cabannes|Cabriès|Cadolive|Carnoux-en-Provence|Carry-le-Rouet|Cassis|Ceyreste|Charleval|" +
    "Châteauneuf-le-Rouge|Châteauneuf-les-Martigues|Châteaurenard|La Ciotat|Cornillon-Confoux|" +
    "Coudoux|Cuges-les-Pins|La Destrousse|Ensuès-la-Redonne|Eygalières|Eyguières|Eyragues|" +
    "La Fare-les-Oliviers|Fontvieille|Fos-sur-Mer|Fuveau|Gardanne|Gignac-la-Nerthe|Grans|" +
    "Graveson|Gréasque|Gémenos|Istres|Jouques|Lamanon|Lambesc|Lançon-Provence|Maillane|" +
    "Mallemort|Marignane|Marseille|Martigues|Mas-Blanc-des-Alpilles|Maussane-les-Alpilles|" +
    "Meyrargues|Meyreuil|Mimet|Miramas|Mollégès|Mouriès|Noves|Orgon|Paradou|" +
    "La Penne-sur-Huveaune|Les Pennes-Mirabeau|Peynier|Peypin|Peyrolles-en-Provence|" +
    "Plan-d'Orgon|Plan-de-Cuques|Port-Saint-Louis-du-Rhône|Port-de-Bouc|Le Puy-Sainte-Réparade|" +
    "Puyloubier|Pélissanne|Rognac|Rognes|Rognonas|La Roque-d'Anthéron|Roquefort-la-Bédoule|" +
    "Roquevaire|Rousset|Le Rove|Saint-Andiol|Saint-Antonin-sur-Bayon|Saint-Cannat|" +
    "Saint-Chamas|Saint-Estève-Janson|Saint-Marc-Jaumegarde|Saint-Martin-de-Crau|" +
    "Saint-Mitre-les-Remparts|Saint-Paul-lès-Durance|Saint-Pierre-de-Mézoargues|" +
    "Saint-Rémy-de-Provence|Saint-Savournin|Saint-Victoret|Saint-Étienne-du-Grès|" +
    "Saintes-Maries-de-la-Mer|Salon-de-Provence|Sausset-les-Pins|Septèmes-les-Vallons|" +
    "Simiane-Collongue|Sénas|Tarascon|Le Tholonet|Trets|Vauvenargues|Velaux|" +
    "Venelles|Ventabren|Vernègues|Verquières|Vitrolles|Éguilles"
  ).split("\\|");

  public static final class Result {
    public final String type, city, status, reason;
    public final int score;
    public final boolean eligible, urgent, geoKnown;
    Result(String type, String city, String status, String reason, int score, boolean eligible, boolean urgent, boolean geoKnown) {
      this.type=type; this.city=city; this.status=status; this.reason=reason;
      this.score=score; this.eligible=eligible; this.urgent=urgent; this.geoKnown=geoKnown;
    }
  }

  public static String normalize(String text) {
    if(text==null)return "";
    String s=Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    s=s.toLowerCase(Locale.ROOT).replace('’',' ').replace('œ','o');
    return s.replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim();
  }
  private static boolean any(String n,String... terms) {
    String bounded=" "+n+" ";
    for(String term:terms) {
      String q=" "+normalize(term)+" ";
      if(bounded.contains(q))return true;
    }
    return false;
  }

  private static boolean mentionsCommune(String norm,String city) {
    String wanted=normalize(city);
    return (" "+norm+" ").contains(" "+wanted+" ");
  }

  public static Result classify(String text,String cityHint) {
    String norm=normalize((text==null?"":text)+" "+(cityHint==null?"":cityHint));
    boolean clim=any(norm,"clim","climatisation","climatiseur","clim réversible","clim reversible",
      "split","monosplit","multisplit","multi split","bi split","gainable","pac air air","pompe a chaleur air air","air conditionne","clim qui fuit");
    boolean eau=any(norm,"chauffe eau","chauffe-eau","cumulus","ballon d eau chaude",
      "ballon eau chaude","ballon electrique","eau chaude","ballon thermodynamique","ballon ecs","chauffe bain",
      "ballon d eau","ballon d eau chaude perce","plus d eau chaude sanitaire");
    if(!clim&&!eau) return new Result("AUTRE","Zone à vérifier","HORS_SUJET",
      "Aucun besoin lié à la climatisation ou au chauffe-eau détecté",0,false,false,false);

    String type=clim && eau ? "CLIM + CHAUFFE-EAU" : clim ? "CLIMATISATION" : "CHAUFFE-EAU";

    boolean sale=any(norm,"je vends","a vendre","vend mon","vendo","vente de materiel",
      "destockage","livraison gratuite","prix ferme","en vente","occasion tres bon etat");
    boolean proAd=any(norm,"je propose mes services","nous proposons nos services",
      "nos prestations","notre entreprise intervient","devis gratuit sans engagement",
      "professionnel a votre service","installateur professionnel disponible",
      "contactez notre entreprise","entreprise specialisee","faites appel a nous");
    if(sale||proAd)return new Result(type,"Zone à vérifier","EXCLU",
      sale?"Annonce de vente de matériel":"Publicité d'un professionnel",0,false,false,false);

    String city=null;
    // The longest names are tested first so Plan-de-Cuques is not confused with Plan-d'Orgon.
    int longest=0;
    for(String candidate:COMMUNES){
      if(mentionsCommune(norm,candidate)&&normalize(candidate).length()>longest){
        city=candidate;longest=normalize(candidate).length();
      }
    }
    if(city==null) {
      // Marseille's 16 arrondissements and all 13xxx postal codes are within the department.
      Matcher postcode=Pattern.compile("(?<![0-9])13[0-9]{3}(?![0-9])").matcher(norm);
      if(postcode.find())city=postcode.group();
      else if(any(norm,"bouches du rhone","departement 13","dans le 13","sur le 13")) city="Bouches-du-Rhône (13)";
    }
    boolean geo=city!=null;
    if(city==null)city="Zone à vérifier";

    boolean request=any(norm,"je cherche","nous cherchons","recherche un","recherche une","je recherche","on recherche","vous connaissez",
      "qui connait","quelqu un pour","quelqu'un pour","besoin de","besoin d",
      "cherche artisan","cherche plombier","cherche installateur","faire installer","faire poser",
      "demande de devis","souhaite installer","souhaiterais installer","pouvez vous installer",
      "qui peut venir","qui pourrait venir","qui peut me conseiller","connaissez vous un",
      "quelqu un connait","quelqu un connaitrait","quelqu un pour","qui pour","urgent plombier",
      "je voudrais installer","j aimerais installer","je veux remplacer","je souhaite remplacer",
      "je dois remplacer","devis pour","besoin d un artisan","a qui faire appel",
      "recommandation d installateur","recommandation plombier",
      "un bon climaticien","bon climaticien","bon plombier","des avis sur un installateur",
      "quel artisan","connaissez vous quelqu un","des recommandations","un contact pour",
      "vous auriez un contact","besoin d aide pour");
    boolean specific=any(norm,"installation","installer","remplacer","remplacement",
      "changer","pose de","pose d","poser","monter","devis","technicien","artisan","plombier","installateur","remise en service","remettre en marche",
      "climaticien","frigoriste","societe","entreprise","changer mon","remplacer mon");
    boolean emergency=any(norm,"plus d eau chaude","panne","hs","perce","fuite",
      "urgent","au plus vite","en urgence","ne fonctionne plus","ne chauffe plus","vient de lacher",
      "en panne","plus de clim","ne fait plus de froid","ne produit plus d eau chaude",
      "ballon perce","cumulus perce","plus rien ne sort");
    boolean repairOnly=any(norm,"entretien annuel","recharge de gaz","nettoyage filtre",
      "nettoyage des filtres","je vends un","pieces detachees","reparation seulement",
      "recherche piece","recherche une piece");

    if(repairOnly && !request)return new Result(type,city,"EXCLU","Entretien ou réparation isolée",0,false,emergency,geo);
    int score=25+(request?32:0)+(specific?16:0)+(emergency?12:0)+(geo?15:0);
    if(!geo)score-=12;
    if(repairOnly)score-=25;
    score=Math.max(0,Math.min(100,score));
    // No cold outreach: a public text is not evidence of consent.
    String status=request&&specific&&geo&&score>=75?"PRIORITAIRE":"A_VERIFIER";
    String reason=(!geo?"Localisation à confirmer. ":"")
      +(request?"Demande explicite détectée. ":"Besoin possible, intention à confirmer. ")
      +(emergency?"Situation potentiellement urgente. ":"")
      +"Aucun contact automatique.";
    return new Result(type,city,status,reason,score,true,emergency,geo);
  }
}
