// Secrets are Supabase server environment variables; never shipped in Android.
const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type' };
Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  const respond = (status: number, body: object) => new Response(JSON.stringify(body), { status, headers: { ...cors, 'Content-Type': 'application/json' } });
  if (req.method !== 'POST') return respond(405, { error: 'method_not_allowed' });
  const url = Deno.env.get('SUPABASE_URL')!;
  const secret = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
  const token = req.headers.get('Authorization')?.replace(/^Bearer /, '');
  if (!token) return respond(401, { error: 'authentication_required' });
  const userResponse = await fetch(`${url}/auth/v1/user`, { headers: { apikey: secret, Authorization: `Bearer ${token}` } });
  if (!userResponse.ok) return respond(401, { error: 'invalid_session' });
  const user = await userResponse.json();
  if (!user.id || user.is_anonymous) return respond(401, { error: 'invalid_user' });
  // Confirm live session with an Authenticated RPC before privileged deletion.
  const live = await fetch(`${url}/rest/v1/rpc/ritmo_my_summary`, { method: 'POST', headers: { apikey: secret, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: '{}' });
  if (!live.ok) return respond(401, { error: 'session_revoked' });
  const adminHeaders = { apikey: secret, Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json' };
  // Remove Auth user first (cascades all ranking data), then reject old JWTs via require_user.
  const deletion = await fetch(`${url}/auth/v1/admin/users/${encodeURIComponent(user.id)}`, { method: 'DELETE', headers: adminHeaders });
  if (!deletion.ok) return respond(503, { error: 'deletion_failed_retry' });
  return respond(200, { deleted: true });
});
