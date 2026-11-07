package fr.franceclim.pro;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Import volontaire de demandes reçues directement via formulaires CSV.
 * Aucun fichier n'est envoyé sur un serveur externe.
 */
public final class CsvIntake {
  private CsvIntake(){}

  public static final class Report {
    public int rows,added,duplicates,ignored,contacts,callbackProofMissing;
    @Override public String toString(){
      return rows+" ligne(s) ; "+added+" dossier(s) ajouté(s), "+duplicates+
        " doublon(s), "+ignored+" hors sujet / incomplet(s). "+
        contacts+" fiche(s) avec coordonnées. "+
        callbackProofMissing+" demande(s) de rappel sans preuve suffisante : appel désactivé.";
    }
  }
  private static String field(Map<String,Integer> heads,List<String> row,String... names){
    for(String name:names){
      Integer index=heads.get(LeadClassifier.normalize(name));
      if(index!=null && index<row.size())return row.get(index).trim();
    }
    return "";
  }
  private static boolean yes(String s){
    String norm=LeadClassifier.normalize(s);
    return norm.equals("oui")||norm.equals("yes")||norm.equals("true")||
      norm.equals("1")||norm.equals("consentement confirme")||norm.equals("accord");
  }
  private static List<List<String>> parse(String data){
    String header=data.split("\\r?\\n",2)[0];
    int semis=0,commas=0,tabs=0;
    boolean quote=false;
    for(int i=0;i<header.length();i++){
      char c=header.charAt(i);
      if(c=='"'){quote=!quote;continue;}
      if(quote)continue;
      if(c==';')semis++;else if(c==',')commas++;else if(c=='\t')tabs++;
    }
    char delim=semis>=commas&&semis>=tabs?';':tabs>commas?'\t':',';
    ArrayList<List<String>> rows=new ArrayList<>();
    ArrayList<String> row=new ArrayList<>();
    StringBuilder cell=new StringBuilder();quote=false;
    for(int i=0;i<data.length();i++){
      char ch=data.charAt(i);
      if(ch=='"'){
        if(quote&&i+1<data.length()&&data.charAt(i+1)=='"'){cell.append('"');i++;}
        else quote=!quote;
      }else if(!quote && (ch==delim||ch=='\n'||ch=='\r')){
        if(cell.length()>4000)throw new IllegalArgumentException("Cellule trop longue");
        row.add(cell.toString());cell.setLength(0);
        if(ch!=delim){
          if(row.size()>40)throw new IllegalArgumentException("Trop de colonnes");
          rows.add(row);row=new ArrayList<>();
          if(rows.size()>310)throw new IllegalArgumentException("Trop de lignes (max 300)");
          if(ch=='\r'&&i+1<data.length()&&data.charAt(i+1)=='\n')i++;
        }
      }else{
        cell.append(ch);
        if(cell.length()>4000)throw new IllegalArgumentException("Cellule trop longue");
      }
    }
    if(quote)throw new IllegalArgumentException("CSV mal formé : guillemet non fermé");
    if(cell.length()>0||!row.isEmpty()){
      row.add(cell.toString());rows.add(row);
    }
    return rows;
  }

  public static Report importFile(Context ctx,InputStream input) throws Exception {
    if(input==null)throw new IllegalArgumentException("Fichier manquant");
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    byte[] buf=new byte[4096];int n;
    while((n=input.read(buf))!=-1){
      if(out.size()+n>750000)throw new IllegalArgumentException("CSV trop volumineux");
      out.write(buf,0,n);
    }
    String csv=out.toString("UTF-8");
    if(csv.startsWith("\uFEFF"))csv=csv.substring(1);
    List<List<String>> rows=parse(csv);
    Report report=new Report();
    if(rows.isEmpty())return report;
    Map<String,Integer> header=new HashMap<>();
    List<String> first=rows.get(0);
    for(int i=0;i<first.size();i++)header.put(LeadClassifier.normalize(first.get(i)),i);
    if(!(header.containsKey("description")||header.containsKey("demande")||
         header.containsKey("message")||header.containsKey("besoin")))
      throw new IllegalArgumentException("En-tête 'description' ou 'demande' nécessaire");
    for(int j=1;j<rows.size();j++){
      List<String> row=rows.get(j);
      report.rows++;
      String city=field(header,row,"ville","commune","city");
      String category=field(header,row,"service","travaux","prestation","type");
      String description=field(header,row,"demande","description","message","besoin");
      String name=field(header,row,"nom","nom client","name","nom prenom","prénom");
      String phone=field(header,row,"telephone","téléphone","tel","mobile","phone");
      String callback=field(header,row,"rappel","demande de rappel","callback","rappel demande");
      String proof=field(header,row,"preuve","preuve de rappel","date demande rappel","date et canal demande","preuve consentement");
      String source=field(header,row,"source","origine","formulaire");
      if(description.isEmpty()||city.isEmpty()){report.ignored++;continue;}
      String text=(description+" "+category).trim();
      // Source explicitement fournie au professionnel ; aucune extraction
      // depuis une base privée ou récupération de coordonnées tierces.
      String label="Formulaire direct / CSV"+(source.isEmpty()?"":" ("+source.substring(0,Math.min(65,source.length()))+")");
      RadarStore.AddResult result=RadarStore.add(ctx,text,city,label);
      if(!result.added){
        if(result.classification!=null&&result.classification.eligible)report.duplicates++;
        else report.ignored++;
        continue;
      }
      report.added++;
      boolean asked=yes(callback)&&proof.length()>=8;
      if(yes(callback)&&!asked)report.callbackProofMissing++;
      boolean ok=RadarStore.setContact(ctx,RadarStore.leads(ctx).length()-1,
        name,phone,asked,proof);
      if(ok && (!name.isEmpty()||!phone.isEmpty()))report.contacts++;
    }
    return report;
  }
}
