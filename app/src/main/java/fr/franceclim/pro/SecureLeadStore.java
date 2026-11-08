package fr.franceclim.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local AES-GCM encryption of CRM records with an Android Keystore key.
 * Legacy plaintext records are migrated only after the encrypted write succeeds.
 * No keys or customer details are sent to GitHub or a server.
 */
final class SecureLeadStore {
  private static final String PREF="franceclim";
  private static final String LEGACY="leads";
  private static final String ENCRYPTED="leads_encrypted_v1";
  private static final String ALIAS="franceclim_radar_leads_aes_v1";
  private SecureLeadStore(){}

  private static SharedPreferences prefs(Context ctx){
    return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
  }
  private static SecretKey key() throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    KeyStore.Entry existing=store.getEntry(ALIAS,null);
    if(existing instanceof KeyStore.SecretKeyEntry)
      return ((KeyStore.SecretKeyEntry)existing).getSecretKey();
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(ALIAS,
      KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256).build());
    return generator.generateKey();
  }

  static String read(Context ctx){
    SharedPreferences p=prefs(ctx);
    String encoded=p.getString(ENCRYPTED,null);
    if(encoded!=null){
      try {
        byte[] bytes=Base64.decode(encoded,Base64.NO_WRAP);
        if(bytes.length<29)throw new IllegalStateException("Données chiffrées invalides");
        byte[] iv=new byte[12];
        System.arraycopy(bytes,0,iv,0,12);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
      }catch(Exception ex){
        // Never silently replace unreadable customer records with an empty inbox.
        throw new IllegalStateException("Dossiers chiffrés illisibles : restaurer une sauvegarde",ex);
      }
    }
    String legacy=p.getString(LEGACY,"[]");
    if(p.contains(LEGACY))save(ctx,legacy);
    return legacy;
  }

  static void save(Context ctx,String json){
    try {
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE,key());
      byte[] iv=cipher.getIV();
      byte[] encrypted=cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
      byte[] combined=new byte[iv.length+encrypted.length];
      System.arraycopy(iv,0,combined,0,iv.length);
      System.arraycopy(encrypted,0,combined,iv.length,encrypted.length);
      if(!prefs(ctx).edit()
        .putString(ENCRYPTED,Base64.encodeToString(combined,Base64.NO_WRAP))
        .remove(LEGACY).commit())
        throw new IllegalStateException("Écriture sécurisée impossible");
    }catch(Exception ex){
      throw new IllegalStateException("Impossible de chiffrer les dossiers",ex);
    }
  }
  static void clear(Context ctx){
    if(!prefs(ctx).edit().remove(ENCRYPTED).remove(LEGACY).commit())
      throw new IllegalStateException("Suppression des dossiers impossible");
  }
}
