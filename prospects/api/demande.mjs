import { validateLead, emailText } from '../lib/validate.mjs';

function message(res,code,error){
  res.setHeader('Cache-Control','no-store');
  res.setHeader('X-Content-Type-Options','nosniff');
  return res.status(code).json({ok:false,error});
}
function configured(env){
  return Boolean(env.TURNSTILE_SECRET && env.TURNSTILE_SITE_KEY &&
    env.TURNSTILE_EXPECTED_HOSTNAME && env.RESEND_API_KEY &&
    env.LEAD_TO_EMAIL && env.LEAD_FROM_EMAIL);
}
export default async function handler(req,res){
  res.setHeader('Cache-Control','no-store');
  res.setHeader('X-Content-Type-Options','nosniff');
  if(req.method !== 'POST') return message(res,405,'Méthode non autorisée.');
  if(!configured(process.env))
    return message(res,503,'Service de demande indisponible pour le moment.');
  if(!/^application\/json(?:\s*;|$)/i.test(String(req.headers?.['content-type'] || '')))
    return message(res,415,'Envoyez une demande JSON.');
  if(Number(req.headers?.['content-length'] || 0)>10000)
    return message(res,413,'Demande trop volumineuse.');
  if(!req.body || typeof req.body !== 'object' || Array.isArray(req.body))
    return message(res,400,'Demande invalide.');
  // Validate the parsed content size even if an attacker falsifies Content-Length.
  if(Buffer.byteLength(JSON.stringify(req.body),'utf8')>10000)
    return message(res,413,'Demande trop volumineuse.');
  const parsed=validateLead(req.body);
  if(parsed.error) return message(res,400,parsed.error);
  const captcha=String(req.body.captcha || '');
  if(captcha.length<10 || captcha.length>2100)
    return message(res,400,'Vérification anti-spam manquante.');
  const id=String(req.body.id || '');
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id))
    return message(res,400,'Identifiant de demande invalide.');

  try{
    const verify=await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify',{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({
        secret:process.env.TURNSTILE_SECRET,
        response:captcha,
        idempotency_key:id
      }),
      signal:AbortSignal.timeout(8000)
    });
    if(!verify.ok) return message(res,502,'Vérification anti-spam indisponible.');
    const verdict=await verify.json();
    if(verdict.success!==true ||
       verdict.hostname!==process.env.TURNSTILE_EXPECTED_HOSTNAME ||
       verdict.action!=='demande_rappel')
      return message(res,400,'Vérification anti-spam refusée.');

    const delivered=await fetch('https://api.resend.com/emails',{
      method:'POST',
      headers:{
        'Authorization':'Bearer '+process.env.RESEND_API_KEY,
        'Content-Type':'application/json',
        'Idempotency-Key':id
      },
      body:JSON.stringify({
        from:process.env.LEAD_FROM_EMAIL,
        to:[process.env.LEAD_TO_EMAIL],
        subject:'Demande de rappel '+parsed.value.service+' — '+parsed.value.ville,
        text:emailText(parsed.value,new Date().toISOString())
      }),
      signal:AbortSignal.timeout(9000)
    });
    if(!delivered.ok) return message(res,502,'Envoi indisponible. Réessayez ultérieurement.');
    const receipt=await delivered.json();
    if(!receipt.id || typeof receipt.id!=='string')
      return message(res,502,'Confirmation d’envoi indisponible.');
    return res.status(200).json({ok:true,message:'Votre demande de rappel a été transmise.'});
  }catch{
    return message(res,502,'Service momentanément indisponible. Réessayez.');
  }
}
