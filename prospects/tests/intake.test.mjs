import test from 'node:test';
import assert from 'node:assert/strict';
import { validateLead, emailText } from '../lib/validate.mjs';
import requestHandler from '../api/demande.mjs';
import configHandler from '../api/config.mjs';

const valid = () => ({
  nom:'Camille Martin',telephone:'06 12 34 56 78',codePostal:'13127',
  ville:'Vitrolles',service:'chauffe-eau',
  besoin:'Mon chauffe-eau est en panne et je souhaite un remplacement.',
  rappel:true,site_web:'',id:'123e4567-e89b-42d3-a456-426614174000',
  captcha:'test-captcha-token-valid'
});
const ENV = Object.freeze({
  TURNSTILE_SECRET:'fake-test',TURNSTILE_SITE_KEY:'fake-test',
  TURNSTILE_EXPECTED_HOSTNAME:'example.test',
  RESEND_API_KEY:'fake-test',LEAD_TO_EMAIL:'test@example.org',
  LEAD_FROM_EMAIL:'expediteur@example.org'
});
const headers = {'content-type':'application/json'};
function response(){
  return {statusCode:200,headers:{},body:null,
    setHeader(k,v){this.headers[k]=v;return this},
    status(n){this.statusCode=n;return this},
    json(x){this.body=x;return this}};
}
async function useEnv(env, fetcher, fn){
  const previous=process.env,oldFetch=globalThis.fetch;
  process.env={...env};globalThis.fetch=fetcher;
  try{return await fn();}
  finally{process.env=previous;globalThis.fetch=oldFetch}
}
function successfulFetch(calls){
  return async (url,opts)=>{
    calls.push({url,opts});
    if(url.includes('siteverify'))
      return {ok:true,json:async()=>({success:true,hostname:'example.test',action:'demande_rappel'})};
    return {ok:true,json:async()=>({id:'email-test-id'})};
  };
}

test('validates a consented 13 request and documents its callback',()=>{
  const result=validateLead(valid());
  assert.equal(result.error,undefined);
  assert.equal(result.value.telephone,'06 12 34 56 78');
  assert.match(emailText(result.value,'2026-11-08T10:00:00Z'),/Demande de rappel : OUI/);
  assert.match(emailText(result.value,'2026-11-08T10:00:00Z'),/\nTéléphone : /);
});
test('rejects out-of-area, missing consent, invalid phone and spam',()=>{
  for(const change of [{codePostal:'34000'},{rappel:false},{telephone:'abc'},
    {service:'autre'},{site_web:'spam'},{besoin:'court'},
    {nom:'<script>alert(1)</script>'},{ville:'X'}])
    assert.ok(validateLead({...valid(),...change}).error,JSON.stringify(change));
});
test('config endpoint stays closed until every secret and site hostname exists',async()=>{
  await useEnv({},undefined,async()=>{
    const res=response();
    configHandler({method:'GET'},res);
    assert.equal(res.body.enabled,false);
    assert.equal(res.body.sitekey,null);
  });
  await useEnv(ENV,undefined,async()=>{
    const res=response();
    configHandler({method:'GET'},res);
    assert.equal(res.body.enabled,true);
    assert.equal(res.body.sitekey,'fake-test');
  });
});
test('no personal data leaves the server when not configured',async()=>{
  let calls=0;
  await useEnv({},async()=>{calls++},async()=>{
    const res=response();
    await requestHandler({method:'POST',headers,body:valid()},res);
    assert.equal(res.statusCode,503);
    assert.equal(calls,0);
  });
});
test('only POST application/json is accepted',async()=>{
  await useEnv(ENV,async()=>{throw Error('Network should not be touched')},async()=>{
    for(const input of [
      {method:'GET',headers,body:valid(),expected:405},
      {method:'POST',headers:{'content-type':'text/plain'},body:valid(),expected:415},
      {method:'POST',headers:{'content-type':'application/json'},body:{...valid(),rappel:false},expected:400},
      {method:'POST',headers,body:{...valid(),id:'bad-id'},expected:400},
      {method:'POST',headers,body:{...valid(),besoin:'x'.repeat(12000)},expected:413},
    ]){
      const res=response();
      await requestHandler(input,res);
      assert.equal(res.statusCode,input.expected);
    }
  });
});
test('requires the exact captcha site and action',async()=>{
  for(const captcha of [
    {success:true,hostname:'attacker.example',action:'demande_rappel'},
    {success:true,hostname:'example.test',action:'other_action'},
    {success:false,hostname:'example.test',action:'demande_rappel'}
  ]){
    let externalCalls=0;
    await useEnv(ENV,async url=>{
      externalCalls++;
      assert.match(url,/siteverify/);
      return {ok:true,json:async()=>captcha};
    },async()=>{
      const res=response();
      await requestHandler({method:'POST',headers,body:valid()},res);
      assert.equal(res.statusCode,400);
      assert.equal(externalCalls,1);
    });
  }
});
test('valid request verifies captcha then submits only to owner mail provider',async()=>{
  const calls=[];
  await useEnv(ENV,successfulFetch(calls),async()=>{
    const res=response();
    await requestHandler({method:'POST',headers,body:valid()},res);
    assert.equal(res.statusCode,200);
    assert.equal(res.body.ok,true);
    assert.equal(calls.length,2);
    assert.match(calls[0].url,/siteverify/);
    assert.equal(calls[1].url,'https://api.resend.com/emails');
    assert.equal(calls[1].opts.headers['Idempotency-Key'],valid().id);
    const sent=JSON.parse(calls[1].opts.body);
    assert.deepEqual(sent.to,['test@example.org']);
    assert.match(sent.text,/06 12 34 56 78/);
  });
});
test('mail provider failure does not falsely confirm lead',async()=>{
  await useEnv(ENV,async url=>url.includes('siteverify')
    ?{ok:true,json:async()=>({success:true,hostname:'example.test',action:'demande_rappel'})}
    :{ok:false,json:async()=>({error:'no'})},async()=>{
      const res=response();
      await requestHandler({method:'POST',headers,body:valid()},res);
      assert.equal(res.statusCode,502);
      assert.equal(res.body.ok,false);
    });
});
test('missing provider receipt is not treated as success',async()=>{
  await useEnv(ENV,async url=>url.includes('siteverify')
    ?{ok:true,json:async()=>({success:true,hostname:'example.test',action:'demande_rappel'})}
    :{ok:true,json:async()=>({})},async()=>{
      const res=response();
      await requestHandler({method:'POST',headers,body:valid()},res);
      assert.equal(res.statusCode,502);
    });
});
test('malformed captcha and request size produce no outgoing calls',async()=>{
  let calls=0;
  await useEnv(ENV,async()=>{calls++},async()=>{
    for(const input of [
      {method:'POST',headers,body:{...valid(),captcha:'short'}},
      {method:'POST',headers:{...headers,'content-length':'12000'},body:valid()},
      {method:'POST',headers,body:null}
    ]){
      const res=response();
      await requestHandler(input,res);
      assert.ok(res.statusCode>=400);
    }
    assert.equal(calls,0);
  });
});
