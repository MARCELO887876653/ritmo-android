import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const db=new PGlite(); let checks=0;
function ok(condition,label) { assert.ok(condition,label); checks++; console.log(`PASS ${label}`); }
async function fails(fn,label) { let failed=false; try {await fn();} catch {failed=true;} ok(failed,label); }
const A='10000000-0000-4000-8000-000000000001', B='20000000-0000-4000-8000-000000000002';
const SA='a0000000-0000-4000-8000-000000000001', SB='b0000000-0000-4000-8000-000000000002';
await db.exec(`create role anon; create role authenticated; create role service_role;
create schema auth;
create table auth.users(id uuid primary key,is_anonymous boolean default false);
create table auth.sessions(id uuid primary key,user_id uuid references auth.users(id) on delete cascade);
create function auth.uid() returns uuid language sql as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
create function auth.jwt() returns jsonb language sql as $$select coalesce(nullif(current_setting('request.jwt.claims',true),''),'{}')::jsonb$$;
grant usage on schema auth to anon,authenticated;
grant execute on function auth.uid(),auth.jwt() to anon,authenticated;
insert into auth.users(id) values('${A}'),('${B}');
insert into auth.sessions values('${SA}','${A}'),('${SB}','${B}');`);
await db.exec(fs.readFileSync(new URL('../supabase/migrations/20261010233000_global_ranking.sql',import.meta.url),'utf8'));
ok(true,'migration runs on PostgreSQL (PGlite); Auth tables are fixtures');
async function as(user) { await db.exec('reset role'); await db.query("select set_config('request.jwt.claim.sub',$1,false),set_config('request.jwt.claims',$2,false)",[user??'',JSON.stringify(user?{session_id:user===A?SA:SB}:{})]); await db.exec(`set role ${user?'authenticated':'anon'}`); }
async function rpc(name,args=[]) {const slots=args.map((_,i)=>`$${i+1}`).join(','); return (await db.query(`select public.${name}(${slots}) as value`,args)).rows[0].value;}
await as(null);
await fails(()=>rpc('ritmo_save_profile',['AliasA',true]),'anon cannot create profile');
await fails(()=>rpc('ritmo_submit_workout',[crypto.randomUUID(),new Date().toISOString(),new Date().toISOString()]),'anon cannot grant XP');
ok((await rpc('ritmo_ranking',['weekly',10])).entries.length===0,'anon sees only public ranking');
await as(A); await rpc('ritmo_save_profile',['AliasA',true]);
await as(B); await rpc('ritmo_save_profile',['AliasB',true]);
await fails(()=>rpc('ritmo_save_profile',['aliasa',true]),'nickname is unique case-insensitively');
await fails(()=>rpc('ritmo_save_profile',['bad nickname',true]),'nickname validation enforced by server');
await db.exec('reset role');
// Backdate activation only in the controlled local fixture, never in the app.
await db.exec("update public.ritmo_profiles set enabled_at=now()-interval '10 days',joined_at=now()-interval '10 days'");
const midnight=new Date(); midnight.setUTCHours(0,0,0,0);
// Deterministic event times after day start and safely in the past.
const now=Date.now(); const base=Math.min(midnight.getTime()+1_000,now-30*60_000);
const event1=crypto.randomUUID();
const span=(n)=>[new Date(base+n*3*60_000).toISOString(),new Date(base+n*3*60_000+60_000).toISOString()];
await as(A);
const first=await rpc('ritmo_submit_workout',[event1,...span(0)]);
ok(first.xp===100&&!first.duplicate,'first valid daily workout earns 100 XP');
const duplicate=await rpc('ritmo_submit_workout',[event1,...span(0)]);
ok(duplicate.xp===100&&duplicate.duplicate,'same event is idempotent');
const second=await rpc('ritmo_submit_workout',[crypto.randomUUID(),...span(1)]);ok(second.xp===25,'second daily workout earns 25 XP');
const rest=await Promise.all([2,3,4].map(n=>rpc('ritmo_submit_workout',[crypto.randomUUID(),...span(n)])));
ok(rest.every(x=>x.xp===0),'further daily workouts earn zero, including simultaneous queued requests');
const summary=await rpc('ritmo_my_summary');ok(summary.total_xp===125&&summary.rewarded_workouts===2,'125 XP limit cannot be bypassed by repetitions');
await fails(()=>db.exec(`insert into public.ritmo_xp_ledger values('${crypto.randomUUID()}','${A}',current_date,100)`),'client cannot insert XP directly');
await fails(()=>db.exec(`update public.ritmo_profiles set ranking_enabled=true where user_id='${B}'`),'client cannot change another profile');
await fails(()=>rpc('ritmo_submit_workout',[crypto.randomUUID(),...span(0)]),'overlapping event rejected');
await fails(()=>rpc('ritmo_submit_workout',[crypto.randomUUID(),new Date(now).toISOString(),new Date(now+600_000).toISOString()]),'future completion rejected');
await fails(()=>rpc('ritmo_submit_workout',[crypto.randomUUID(),new Date(now-60_000).toISOString(),new Date(now-1_001).toISOString()]),'workout below one minute rejected');
await fails(()=>rpc('ritmo_submit_workout',[crypto.randomUUID(),new Date(now-9*86_400_000).toISOString(),new Date(now-9*86_400_000+60_000).toISOString()]),'event older than seven days rejected');
await fails(()=>rpc('ritmo_submit_workout',[crypto.randomUUID(),new Date(now-11*86_400_000).toISOString(),new Date(now-11*86_400_000+60_000).toISOString()]),'retroactive event before participation rejected');
await as(B);
ok((await db.query('select * from public.ritmo_profiles')).rows.length===1,'RLS profiles exposes own profile only');
ok((await db.query('select * from public.ritmo_workout_events')).rows.length===0,'RLS events hides another account');
ok((await db.query('select * from public.ritmo_xp_ledger')).rows.length===0,'RLS ledger hides another account');
await fails(()=>rpc('ritmo_submit_workout',[event1,...span(0)]),'another account cannot claim existing event');
await rpc('ritmo_submit_workout',[crypto.randomUUID(),...span(0)]);
let ranking=await rpc('ritmo_ranking',['all',100]);
ok(ranking.entries[0].nickname==='AliasA'&&ranking.me.position===2,'global ranking returns own position');
ok(!JSON.stringify(ranking).includes('user_id')&&!JSON.stringify(ranking).includes('email')&&!JSON.stringify(ranking).includes('started_at'),'public projection omits private data');
for(const p of ['weekly','monthly','all']) ok((await rpc('ritmo_ranking',[p,10])).period===p,`${p} ranking accepted`);
await fails(()=>rpc('ritmo_ranking',['invalid',100]),'invalid period rejected');
await fails(()=>rpc('ritmo_ranking',['all',100000]),'unbounded ranking request rejected');
await rpc('ritmo_save_profile',['AliasB',false]);
ranking=await rpc('ritmo_ranking',['all',100]);ok(ranking.me===null&&ranking.entries.length===1,'private account disappears from ranking');
await fails(()=>rpc('ritmo_submit_workout',[crypto.randomUUID(),...span(2)]),'disabled participation cannot submit new events');
await db.exec('reset role');
await db.exec(`delete from auth.sessions where id='${SA}'`);
await as(A); await fails(()=>rpc('ritmo_my_summary'),'revoked session rejected even with stale JWT');
await db.exec('reset role'); await db.exec(`delete from auth.users where id='${A}'`);
ok((await db.query('select * from public.ritmo_xp_ledger where user_id=$1',[A])).rows.length===0,'deletion cascades XP ledger');
ok((await db.query('select * from public.ritmo_workout_events where user_id=$1',[A])).rows.length===0,'deletion cascades workout events');
ok((await db.query('select * from public.ritmo_profiles where user_id=$1',[A])).rows.length===0,'deletion cascades profile');
await as(A); await fails(()=>rpc('ritmo_save_profile',['Deleted',true]),'deleted account cannot resurrect itself with stale JWT');
await db.exec('reset role');
// Period boundaries and Top-100 own-position use controlled historical fixtures.
const C='30000000-0000-4000-8000-000000000003';
await db.exec(`insert into auth.users values('${C}',false); insert into public.ritmo_profiles(user_id,nickname,ranking_enabled) values('${C}','History',true);`);
const weekStart=new Date(); const wd=(weekStart.getUTCDay()+6)%7; weekStart.setUTCDate(weekStart.getUTCDate()-wd); weekStart.setUTCHours(0,0,0,0);
const monthStart=new Date(); monthStart.setUTCDate(1); monthStart.setUTCHours(0,0,0,0);
for(const [date,xp] of [[new Date(now-120_000),100],[new Date(weekStart.getTime()-120_000),25],[new Date(monthStart.getTime()-120_000),100]]) {
 const id=crypto.randomUUID();
 await db.query('insert into public.ritmo_workout_events(event_id,user_id,started_at,completed_at,xp_day) values($1,$2,$3,$4,$5)',[id,C,new Date(date.getTime()-60_000).toISOString(),date.toISOString(),date.toISOString().slice(0,10)]);
 await db.query('insert into public.ritmo_xp_ledger values($1,$2,$3,$4,now())',[id,C,date.toISOString().slice(0,10),xp]);
}
await as(null);
let all=(await rpc('ritmo_ranking',['all',100])).entries.find(e=>e.nickname==='History').xp;
let week=(await rpc('ritmo_ranking',['weekly',100])).entries.find(e=>e.nickname==='History').xp;
let month=(await rpc('ritmo_ranking',['monthly',100])).entries.find(e=>e.nickname==='History').xp;
ok(all===225&&week===100&&month<all,'weekly/monthly boundaries actually exclude older XP');
await db.exec('reset role');
for(let i=0;i<120;i++) {
 const id=crypto.randomUUID(); await db.query('insert into auth.users(id) values($1)',[id]);
 await db.query('insert into public.ritmo_profiles(user_id,nickname,ranking_enabled) values($1,$2,true)',[id,`User${String(i).padStart(3,'0')}`]);
}
await as(B); await rpc('ritmo_save_profile',['zzLast',true]);
ok((await rpc('ritmo_ranking',['all',10])).entries.length===10,'Top 10 is bounded');
ok((await rpc('ritmo_ranking',['all',100])).entries.length===100,'Top 100 is bounded');
// B has 100 XP; make the remaining users outrank B through fixtures.
await db.exec('reset role');
await db.exec("update public.ritmo_profiles set ranking_enabled=false where user_id='"+C+"'");
await as(B); await rpc('ritmo_save_profile',['zzLast',false]); await rpc('ritmo_save_profile',['zzLast',true]);
await db.exec('reset role'); await db.exec("delete from public.ritmo_workout_events where user_id='"+B+"'");
await as(B); const beyond=await rpc('ritmo_ranking',['all',100]);
ok(beyond.me.position>100&&beyond.entries.length===100,'own position is returned outside Top 100');
console.log(`${checks} PostgreSQL checks passed. Supabase hosted Auth/REST/Edge are not exercised by this suite.`);
await db.close();
