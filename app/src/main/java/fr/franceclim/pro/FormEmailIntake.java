package fr.franceclim.pro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser local pour un courriel de demande de rappel issu du formulaire France Clim.
 * L'utilisateur doit partager volontairement le courriel dans l'application.
 * Le texte d'un courriel n'authentifie pas son expéditeur : avant tout appel, vérifier
 * que le message provient effectivement du formulaire et que le rappel est autorisé.
 */
public final class FormEmailIntake {
  private FormEmailIntake(){}

  public static final class Request {
    public final String id,name,phone,city,postal,service,detail,evidence;
    Request(String id,String name,String phone,String city,String postal,
      String service,String detail,String evidence){
      this.id=id;this.name=name;this.phone=phone;this.city=city;
      this.postal=postal;this.service=service;this.detail=detail;this.evidence=evidence;
    }
  }

  private static String field(String text,String label){
    Matcher m=Pattern.compile("(?m)^"+Pattern.quote(label)+"[ \\t]*:[ \\t]*(.*)\\r?$")
      .matcher(text);
    return m.find()?m.group(1).trim():"";
  }

  public static Request parse(String text){
    if(text==null || text.length()>16000 ||
       !text.contains("Nouvelle demande volontaire de rappel — France Clim Radar 13"))
      return null;
    String id=field(text,"Identifiant");
    String name=field(text,"Nom");
    String phone=field(text,"Téléphone");
    String cityPostal=field(text,"Ville");
    String service=field(text,"Travaux");
    String callback=field(text,"Demande de rappel");
    String date=field(text,"Date UTC");
    String source=field(text,"Source");
    String proof=field(text,"Preuve");
    Matcher place=Pattern.compile("^(.{2,80}) \\(?(13[0-9]{3})\\)?$")
      .matcher(cityPostal);
    if(!place.matches()||!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")||
       !name.matches("[\\p{L} '-]{2,80}")||
       !phone.matches("(?:\\+33|0)[1-9](?:[ .-]?[0-9]{2}){4}")||
       !("climatisation".equals(service)||"chauffe-eau".equals(service))||
       !"OUI, formulaire direct".equals(callback)||
       !"formulaire web /demande".equals(source)||
       !"case non précochée de demande explicite de rappel".equals(proof)||
       !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z"))
      return null;

    Matcher details=Pattern.compile("(?s)(?:^|\\n)Besoin :[ \\t]*(.*?)\\r?\\nDemande de rappel :")
      .matcher(text);
    if(!details.find())return null;
    String detail=details.group(1).trim();
    if(detail.length()<15||detail.length()>1200)return null;
    String city=place.group(1).trim();
    String postal=place.group(2);
    String evidence="Courriel du formulaire web /demande, identifiant "+id+
      ", horodatage "+date+". Vérifier la provenance du courriel avant tout rappel.";
    return new Request(id,name,phone,city,postal,service,detail,evidence);
  }
}
