import { validateLead, emailText } from './_validate.mjs';

function message(res, code, error){
  res.setHeader('Cache-Control','no-store');
  return res.status(code).json({ok:false,error});
}
export default async function handler(req,res){
  res.setHeader('Cache-Control','no-store');
  if(req.method !== 'POST') return message(res,405,'Méthode non autorisée.');
  const {TURNSTILE_SECRET,RESEND_API_KEY,LEAD_TO_EMAIL,LEAD_FROM_EMAIL,TURNSTILE_SITE_KEY} = process.env;
  if(!TURNSTILE_SECRET || !RESEND_API_KEY || !LEAD_TO_EMAIL || !LEAD_FROM_EMAIL || !TURNSTILE_SITE_KEY)
    return message(res,503,'Service de demande indisponible pour le moment.');
  if(Number(req.headers['content-length'] || 0)>10000)
    return message(res,413,'Demande trop volumineuse.');
  const body=req.body;
  const parsed=validateLead(body);
  if(parsed.error) return message(res,400,parsed.error);
  const captcha=String(body?.captcha || '');
  if(captcha.length<10 || captcha.length>2100) return message(res,400,'Vérification anti-spam manquante.');
  try {
    const ip=String(req.headers['x-real-ip'] || '').slice(0,60);
    const verify=await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify',{
      method:'POST',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({secret:TURNSTILE_SECRET,response:captcha,remoteip:ip}),
      signal:AbortSignal.timeout(8000)
    });
    if(!verify.ok) return message(res,502,'Vérification anti-spam indisponible.');
    const result=await verify.json();
    if(result.success!==true) return message(res,400,'Vérification anti-spam refusée.');
    const id=String(body?.id || '');
    if(!/^[0-9a-f-]{36}$/i.test(id)) return message(res,400,'Identifiant de demande invalide.');
    const delivered=await fetch('https://api.resend.com/emails',{
      method:'POST',
      headers:{'Authorization':'Bearer '+RESEND_API_KEY,
        'Content-Type':'application/json','Idempotency-Key':id},
      body:JSON.stringify({
        from:LEAD_FROM_EMAIL,to:[LEAD_TO_EMAIL],
        subject:'Demande de rappel '+parsed.value.service+' — '+parsed.value.ville,
        text:emailText(parsed.value,new Date().toISOString())
      }),
      signal:AbortSignal.timeout(9000)
    });
    if(!delivered.ok) return message(res,502,'Envoi indisponible. Réessayez ultérieurement.');
    const response=await delivered.json();
    if(!response.id) return message(res,502,'Confirmation d’envoi indisponible.');
    return res.status(200).json({ok:true,message:'Votre demande de rappel a été transmise.'});
  } catch {
    return message(res,502,'Service momentanément indisponible. Réessayez.');
  }
}
