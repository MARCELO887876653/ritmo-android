"""Read-only and rejected-request checks against the configured hosted backend.
Does not create users, grant XP, or prove successful authenticated flows.
"""
from pathlib import Path
import json, urllib.request, urllib.error, concurrent.futures
root=Path(__file__).resolve().parent.parent
props=dict(line.split('=',1) for line in (root/'backend.properties.example').read_text().splitlines() if '=' in line and not line.startswith('#'))
checks=[
 ('auth_settings','/auth/v1/settings','GET',None,200,None),
 ('ranking_weekly','/rest/v1/rpc/ritmo_ranking','POST',{'p_period':'weekly','p_limit':10},200,None),
 ('ranking_monthly','/rest/v1/rpc/ritmo_ranking','POST',{'p_period':'monthly','p_limit':100},200,None),
 ('ranking_all','/rest/v1/rpc/ritmo_ranking','POST',{'p_period':'all','p_limit':10},200,None),
 ('guest_summary_denied','/rest/v1/rpc/ritmo_my_summary','POST',{},401,None),
 ('guest_profile_denied','/rest/v1/rpc/ritmo_get_profile','POST',{},401,None),
 ('guest_delete_denied','/functions/v1/delete-account','POST',{},401,None),
 ('invalid_session_delete_denied','/functions/v1/delete-account','POST',{},401,'invalid-session'),
 ('guest_ledger_table_denied','/rest/v1/ritmo_xp_ledger','GET',None,401,None),
 ('invalid_ranking_period','/rest/v1/rpc/ritmo_ranking','POST',{'p_period':'bad','p_limit':10},400,None),
 ('excess_ranking_limit','/rest/v1/rpc/ritmo_ranking','POST',{'p_period':'all','p_limit':10000},400,None),
]
def test(item):
 name,path,method,body,expected,token=item
 headers={'apikey':props['supabasePublishableKey'],'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(props['supabaseUrl']+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=40) as r:status=r.status;data=json.load(r)
 except urllib.error.HTTPError as e:status=e.code;data=json.load(e)
 passed=status==expected
 if name.startswith('ranking_'):passed=passed and all(set(x)<=set(['position','nickname','xp','level']) for x in data.get('entries',[]))
 return {'name':name,'status':status,'expected':expected,'passed':passed,'body':data}
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:results=list(executor.map(test,checks))
print(json.dumps({'passed':sum(r['passed'] for r in results),'failed':sum(not r['passed'] for r in results),'checks':results},indent=2))
raise SystemExit(0 if all(r['passed'] for r in results) else 1)
