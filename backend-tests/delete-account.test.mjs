import assert from 'node:assert/strict';
let handler;
globalThis.Deno={env:{get:key=>({SUPABASE_URL:'https://fixture.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'server-only-secret'})[key]},serve:fn=>handler=fn};
await import('../supabase/functions/delete-account/index.ts');
let mode='ok'; let calls=[];
globalThis.fetch=async(url,options)=>{
 calls.push([url,options]);
 if(url.endsWith('/auth/v1/user')) return new Response(JSON.stringify({id:'verified-user',is_anonymous:mode==='anonymous'}),{status:mode==='invalid'?401:200});
 if(url.endsWith('/rpc/ritmo_my_summary')) return new Response('{}',{status:mode==='revoked'?403:200});
 if(url.endsWith('/admin/users/verified-user')) {assert.equal(options.headers.Authorization,'Bearer server-only-secret');return new Response('{}',{status:mode==='failure'?503:200});}
 throw new Error('unexpected request '+url);
};
const req=(method='POST',token='valid-user-token')=>new Request('https://fixture.supabase.co/functions/v1/delete-account',{method,headers:token?{Authorization:`Bearer ${token}`}:{}});
for(const [scenario,method,token,expected] of [['options','OPTIONS',null,200],['method','GET',null,405],['missing','POST',null,401],['invalid','POST','bad',401],['anonymous','POST','valid',401],['revoked','POST','valid',401],['failure','POST','valid',503],['ok','POST','valid',200]]) {
 mode=scenario; calls=[]; const response=await handler(req(method,token)); assert.equal(response.status,expected);
 if(scenario==='ok') {assert.equal((await response.json()).deleted,true);assert.equal(calls.length,3);assert.equal(calls[1][1].headers.Authorization,'Bearer valid');}
 else if(expected===401) assert.ok(!calls.some(([url])=>url.includes('/admin/users/')));
 console.log('PASS deletion endpoint '+scenario);
}
console.log('8 Edge Function checks passed with mocked Auth/REST responses. Hosted Supabase is not exercised.');
