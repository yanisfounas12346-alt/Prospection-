export default function handler(req,res){
  res.setHeader('Cache-Control','no-store');
  if(req.method !== 'GET') return res.status(405).json({error:'Méthode non autorisée'});
  const sitekey=process.env.TURNSTILE_SITE_KEY;
  const enabled=Boolean(sitekey && process.env.TURNSTILE_SECRET &&
    process.env.RESEND_API_KEY && process.env.LEAD_TO_EMAIL && process.env.LEAD_FROM_EMAIL &&
    process.env.TURNSTILE_EXPECTED_HOSTNAME);
  return res.status(200).json({enabled,sitekey:enabled?sitekey:null});
}
