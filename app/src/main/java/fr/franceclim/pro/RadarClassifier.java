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
  /** Un seul moteur de qualification pour les flux et les notifications.
   * Les demandes plausibles restent visibles même lorsque leur intention est incertaine.
   * Les publications sans localisation dans le 13 sont écartées des flux publics
   * pour éviter de saturer la carte avec des annonces hors département.
   */
  public static Result analyse(String text){
    if(text==null||text.trim().length()<12)return null;
    LeadClassifier.Result lead=LeadClassifier.classify(text,"");
    if(!lead.eligible||!lead.geoKnown)return null;
    return new Result(lead.type,lead.city,lead.reason,lead.score);
  }
}
