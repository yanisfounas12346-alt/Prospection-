const clean = value => String(value ?? '').trim();
export function validateLead(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { error: 'Demande invalide.' };
  const name = clean(raw.nom), phone = clean(raw.telephone), postal = clean(raw.codePostal);
  const city = clean(raw.ville), detail = clean(raw.besoin), service = clean(raw.service);
  const proof = raw.rappel === true;
  if (clean(raw.site_web)) return { error: 'Demande non acceptée.' };
  if (!/^[\\p{L} '-]{2,80}$/u.test(name)) return { error: 'Précisez votre nom.' };
  if (!/^(?:\\+33|0)[1-9](?:[ .-]?\\d{2}){4}$/.test(phone)) return { error: 'Numéro de téléphone français invalide.' };
  if (!/^13\\d{3}$/.test(postal)) return { error: 'Le service couvre uniquement le département 13.' };
  if (!/^[\\p{L} '-]{2,80}$/u.test(city)) return { error: 'Précisez votre commune.' };
  if (!['climatisation','chauffe-eau'].includes(service)) return { error: 'Sélectionnez les travaux.' };
  if (detail.length < 15 || detail.length > 1200) return { error: 'Décrivez votre besoin (15 à 1200 caractères).' };
  if (!proof) return { error: 'Vous devez demander explicitement à être rappelé.' };
  return { value: { nom: name, telephone: phone, codePostal: postal, ville: city, besoin: detail, service } };
}
export const emailText = (lead, date) => [
  'Nouvelle demande volontaire de rappel — France Clim Radar 13',
  'Nom : ' + lead.nom,
  'Téléphone : ' + lead.telephone,
  'Ville : ' + lead.ville + ' (' + lead.codePostal + ')',
  'Travaux : ' + lead.service,
  'Besoin : ' + lead.besoin,
  'Demande de rappel : OUI, formulaire direct',
  'Date UTC : ' + date,
  'Source : formulaire web /demande',
  'Preuve : case non précochée de demande explicite de rappel'
].join('\\n');
