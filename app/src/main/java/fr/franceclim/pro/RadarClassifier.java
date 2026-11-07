package fr.franceclim.pro;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Qualification heuristique de demandes de particuliers, sans prise de contact. */
public final class RadarClassifier {
  private RadarClassifier() {}
  public static final class Result {
    public final String category, city, reason;
    public final int score;
    Result(String category,String city,String reason,int score){
      this.category=category;this.city=city;this.reason=reason;this.score=score;
    }
  }

  // Référentiel des 119 communes du département 13 (noms COG INSEE).
  private static final String COMMUNES =
    "Aix-en-Provence|Allauch|Alleins|Arles|Aubagne|Aureille|Auriol|Aurons|La Barben|Barbentane|Les Baux-de-Provence|Beaurecueil|Belcodène|Berre-l'Étang|Bouc-Bel-Air|La Bouilladisse|Boulbon|Cabannes|Cabriès|Cadolive|Carnoux-en-Provence|Carry-le-Rouet|Cassis|Ceyreste|Charleval|Châteauneuf-le-Rouge|Châteauneuf-les-Martigues|Châteaurenard|La Ciotat|Cornillon-Confoux|Coudoux|Cuges-les-Pins|La Destrousse|Ensuès-la-Redonne|Eygalières|Eyguières|Eyragues|La Fare-les-Oliviers|Fontvieille|Fos-sur-Mer|Fuveau|Gardanne|Gignac-la-Nerthe|Grans|Graveson|Gréasque|Gémenos|Istres|Jouques|Lamanon|Lambesc|Lançon-Provence|Maillane|Mallemort|Marignane|Marseille|Martigues|Mas-Blanc-des-Alpilles|Maussane-les-Alpilles|Meyrargues|Meyreuil|Mimet|Miramas|Mollégès|Mouriès|Noves|Orgon|Paradou|La Penne-sur-Huveaune|Les Pennes-Mirabeau|Peynier|Peypin|Peyrolles-en-Provence|Plan-d'Orgon|Plan-de-Cuques|Port-Saint-Louis-du-Rhône|Port-de-Bouc|Le Puy-Sainte-Réparade|Puyloubier|Pélissanne|Rognac|Rognes|Rognonas|La Roque-d'Anthéron|Roquefort-la-Bédoule|Roquevaire|Rousset|Le Rove|Saint-Andiol|Saint-Antonin-sur-Bayon|Saint-Cannat|Saint-Chamas|Saint-Estève-Janson|Saint-Marc-Jaumegarde|Saint-Martin-de-Crau|Saint-Mitre-les-Remparts|Saint-Paul-lès-Durance|Saint-Pierre-de-Mézoargues|Saint-Rémy-de-Provence|Saint-Savournin|Saint-Victoret|Saint-Étienne-du-Grès|Saintes-Maries-de-la-Mer|Salon-de-Provence|Sausset-les-Pins|Septèmes-les-Vallons|Simiane-Collongue|Sénas|Tarascon|Le Tholonet|Trets|Vauvenargues|Velaux|Venelles|Ventabren|Vernègues|Verquières|Vitrolles|Éguilles";

  public static String normal(String s){
    String n=Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
      .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
    return " "+n+" ";
  }
  private static boolean has(String s,String... terms){
    for(String t:terms) if(s.contains(normal(t)))return true;
    return false;
  }
  public static Result analyse(String text){
    String s=normal(text);
    if(s.length()<22) return null;
    boolean clim=has(s,"climatisation","climatiseur","clim","multi split","monosplit","bi split","gainable","pompe à chaleur air air","pac air air");
    boolean eau=has(s,"chauffe eau","cumulus","ballon d eau chaude","ballon électrique","plus d eau chaude","ballon thermodynamique");
    if(!clim&&!eau)return null;
    // Ventes de matériel et offres de professionnels ne sont pas des demandes.
    if(has(s,"je vends","à vendre","a vendre","vends mon","vend mon","vente de","occasion",
       "je propose mes services","nous proposons","artisan qualifié","entreprise spécialisée",
       "contactez nous","nos prestations","tarif installation","promo installation","recrutons"))return null;
    if(has(s,"entretien annuel","nettoyage filtre","recharge de gaz","recherche de fuite uniquement"))return null;

    boolean explicit=has(s,"je cherche","je recherche","cherche un","recherche un","besoin d un",
      "besoin d une","besoin de","quelqu un connait","quelqu un connaitrait","vous connaissez",
      "devis pour","faire installer","faire poser","faire remplacer","faire changer","qui peut installer",
      "qui peut remplacer","qui pour installer","trouver un installateur","trouver un plombier",
      "j aimerais installer","je voudrais installer","je souhaite installer","je veux remplacer");
    boolean urgent=has(s,"plus d eau chaude","en panne","hs","percé","fuit","fuite","urgence","urgent",
      "ne fonctionne plus","ne chauffe plus","vient de lâcher","à remplacer rapidement");
    boolean firstPerson=has(s,"mon chauffe eau","mon cumulus","mon ballon","ma clim","chez moi","ma maison",
      "mon appartement","notre maison","notre appartement");
    if(!explicit && !(urgent&&firstPerson))return null;
    if(has(s,"réparation uniquement","petit dépannage uniquement","entretien uniquement"))return null;

    String city="";
    String[] cities=COMMUNES.split("\\|");
    // Trier implicitement par longueur pour éviter "Arles" dans une autre mention.
    for(String c:cities){
      String n=normal(c);
      if(s.contains(n) && c.length()>city.length())city=c;
    }
    if(city.isEmpty()) {
      Matcher postal=Pattern.compile("(?<![0-9])13[0-9]{3}(?![0-9])").matcher(s);
      if(postal.find())city=postal.group();
    }
    if(city.isEmpty() && has(s,"bouches du rhone","département 13","dans le 13"))city="Bouches-du-Rhône";
    // Sans preuve de localisation dans le 13, aucune alerte automatique.
    if(city.isEmpty())return null;

    int score=50 + (explicit?17:0) + (urgent?18:0) + (firstPerson?8:0) +
      (city.equals("Bouches-du-Rhône")?0:7);
    return new Result(eau?"Chauffe-eau":"Climatisation",city,
      urgent?"Remplacement / panne urgente":"Demande d'installation ou de remplacement",Math.min(100,score));
  }
}
