import test from 'node:test';
import assert from 'node:assert/strict';
import { validateLead, emailText } from '../lib/validate.mjs';
import requestHandler from '../api/demande.mjs';
import configHandler from '../api/config.mjs';

const valid = () => ({
 nom:'Camille Martin', telephone:'06 12 34 56 78',
 codePostal:'13127', ville:'Vitrolles',
 service:'chauffe-eau',
 besoin:'Mon chauffe-eau est en panne et je souhaite un remplacement.',
 rappel:true, site_web:'', id:'123e4567-e89b-12d3-a456-426614174000',captcha:'valid-test-token'
});
function response(){
 return {statusCode:200,headers:{},body:null,
 setHeader(k,v){this.headers[k]=v;return this},
 status(n){this.statusCode=n;return this},
 json(x){this.body=x;return this}
 };
}
test('accepts voluntary request from the 13',()=>{
 const result=validateLead(valid());
 assert.equal(result.error,undefined);
 assert.equal(result.value.telephone,'06 12 34 56 78');
 assert.match(emailText(result.value,'2026-11-08T10:00:00Z'),/Demande de rappel : OUI/);
});
test('rejects non-local or non-consenting requests',()=>{
 for(const change of [{codePostal:'34000'},{rappel:false},{telephone:'abc'},
   {service:'autre'},{site_web:'spam'},{besoin:'court'}]){
   assert.ok(validateLead({...valid(),...change}).error);
 }
});
test('form is disabled unless all mail and spam credentials are configured',()=>{
 const res=response();
 const original=process.env;
 process.env={};
 try {configHandler({method:'GET'},res);assert.equal(res.body.enabled,false)}
 finally {process.env=original}
});
test('no personal data sent when delivery is not configured',async()=>{
 const original=process.env, oldFetch=globalThis.fetch;
 let calls=0;process.env={};globalThis.fetch=async()=>{calls++};
 try {
  const res=response();
  await requestHandler({method:'POST',headers:{},body:valid()},res);
  assert.equal(res.statusCode,503);
  assert.equal(calls,0);
 } finally {process.env=original;globalThis.fetch=oldFetch}
});
test('successful request requires spam verification and provider message id',async()=>{
 const original=process.env, oldFetch=globalThis.fetch;
 process.env={TURNSTILE_SECRET:'test',TURNSTILE_SITE_KEY:'test',RESEND_API_KEY:'test',LEAD_TO_EMAIL:'a@example.org',LEAD_FROM_EMAIL:'b@example.org'};
 const called=[];
 globalThis.fetch=async(url,opts)=>{
  called.push({url,opts});
  if(url.includes('siteverify'))return {ok:true,json:async()=>({success:true})};
  return {ok:true,json:async()=>({id:'email-test-id'})};
 };
 try{
  const res=response();
  await requestHandler({method:'POST',headers:{},body:valid()},res);
  assert.equal(res.statusCode,200);
  assert.equal(res.body.ok,true);
  assert.equal(called.length,2);
  assert.equal(called[1].opts.headers['Idempotency-Key'],valid().id);
  const sent=JSON.parse(called[1].opts.body);
  assert.match(sent.text,/06 12 34 56 78/);
 } finally {process.env=original;globalThis.fetch=oldFetch}
});
test('provider rejection does not claim successful callback request',async()=>{
 const original=process.env, oldFetch=globalThis.fetch;
 process.env={TURNSTILE_SECRET:'test',TURNSTILE_SITE_KEY:'test',RESEND_API_KEY:'test',LEAD_TO_EMAIL:'a@example.org',LEAD_FROM_EMAIL:'b@example.org'};
 globalThis.fetch=async url=>url.includes('siteverify')
  ?{ok:true,json:async()=>({success:true})}
  :{ok:false,json:async()=>({error:'no'})};
 try{
  const res=response();
  await requestHandler({method:'POST',headers:{},body:valid()},res);
  assert.equal(res.statusCode,502);
  assert.equal(res.body.ok,false);
 } finally {process.env=original;globalThis.fetch=oldFetch}
});
