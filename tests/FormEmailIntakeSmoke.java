package fr.franceclim.pro;

public final class FormEmailIntakeSmoke {
  private static void check(boolean ok,String label){
    if(!ok)throw new AssertionError(label);
  }
  public static void main(String[] ignored){
    String mail="Nouvelle demande volontaire de rappel — France Clim Radar 13\n"
      +"Identifiant : 123e4567-e89b-42d3-a456-426614174000\n"
      +"Nom : Marie Dupont\nTéléphone : 06 12 34 56 78\n"
      +"Ville : Vitrolles (13127)\nTravaux : chauffe-eau\n"
      +"Besoin : Mon chauffe-eau est en panne, je souhaite le remplacer.\n"
      +"Demande de rappel : OUI, formulaire direct\n"
      +"Date UTC : 2026-11-08T12:30:00.000Z\n"
      +"Source : formulaire web /demande\n"
      +"Preuve : case non précochée de demande explicite de rappel\n";
    FormEmailIntake.Request r=FormEmailIntake.parse(mail);
    check(r!=null,"courriel conforme");
    check(r.phone.equals("06 12 34 56 78"),"telephone");
    check(r.postal.equals("13127")&&r.city.equals("Vitrolles"),"commune");
    check(r.evidence.contains("123e4567-e89b-42d3-a456-426614174000"),"identifiant");
    check(FormEmailIntake.parse(mail.replace("OUI, formulaire direct","NON"))==null,"accord absent");
    check(FormEmailIntake.parse(mail.replace("13127","75001"))==null,"hors departement");
    check(FormEmailIntake.parse(mail.replace("06 12 34 56 78","abc"))==null,"telephone invalide");
    check(FormEmailIntake.parse(mail.replace("formulaire web /demande","inconnu"))==null,"source inconnue");
    check(FormEmailIntake.parse("Annonce de climatisation Vitrolles")==null,"publication normale");
    System.out.println("OK: import de courriel structure et validations.");
  }
}
