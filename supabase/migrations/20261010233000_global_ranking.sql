-- Ritmo 1.1.0. Private workout histories remain on the device.
create schema if not exists ritmo_private;
revoke all on schema ritmo_private from public;
grant usage on schema ritmo_private to anon, authenticated;

create table public.ritmo_profiles (
 user_id uuid primary key references auth.users(id) on delete cascade,
 nickname text not null check (nickname ~ '^[A-Za-z0-9_]{3,24}$'),
 ranking_enabled boolean not null default false,
 joined_at timestamptz not null default now(),
 enabled_at timestamptz,
 created_at timestamptz not null default now()
);
create unique index ritmo_nickname_unique on public.ritmo_profiles(lower(nickname));
alter table public.ritmo_profiles enable row level security;
create policy own_profile on public.ritmo_profiles for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.ritmo_profiles from anon, authenticated;
grant select on public.ritmo_profiles to authenticated;

create table public.ritmo_workout_events (
 event_id uuid primary key,
 user_id uuid not null references public.ritmo_profiles(user_id) on delete cascade,
 started_at timestamptz not null,
 completed_at timestamptz not null,
 xp_day date not null,
 received_at timestamptz not null default now(),
 check(completed_at >= started_at + interval '60 seconds')
);
create index ritmo_events_user_day on public.ritmo_workout_events(user_id, xp_day);
alter table public.ritmo_workout_events enable row level security;
create policy own_events on public.ritmo_workout_events for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.ritmo_workout_events from anon, authenticated;
grant select on public.ritmo_workout_events to authenticated;

create table public.ritmo_xp_ledger (
 event_id uuid primary key references public.ritmo_workout_events(event_id) on delete cascade,
 user_id uuid not null references public.ritmo_profiles(user_id) on delete cascade,
 xp_day date not null,
 xp integer not null check (xp in (0,25,100)),
 created_at timestamptz not null default now()
);
create index ritmo_xp_user_day on public.ritmo_xp_ledger(user_id, xp_day) include(xp);
create index ritmo_xp_day_user on public.ritmo_xp_ledger(xp_day, user_id) include(xp);
alter table public.ritmo_xp_ledger enable row level security;
create policy own_xp on public.ritmo_xp_ledger for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.ritmo_xp_ledger from anon, authenticated;
grant select on public.ritmo_xp_ledger to authenticated;

-- Check the live Auth user/session, not editable metadata or a stale JWT alone.
create function ritmo_private.require_user() returns uuid language plpgsql security definer set search_path = '' as $$
declare u uuid := auth.uid(); s uuid;
begin
 if u is null or not exists(select 1 from auth.users where id=u and coalesce(is_anonymous,false)=false) then
  raise exception 'authentication_required' using errcode='28000';
 end if;
 s := nullif(auth.jwt()->>'session_id','')::uuid;
 if s is null or not exists(select 1 from auth.sessions where id=s and user_id=u) then
  raise exception 'session_revoked' using errcode='28000';
 end if;
 return u;
end $$;
revoke all on function ritmo_private.require_user() from public, anon, authenticated;

create function ritmo_private.profile(p_nickname text, p_enabled boolean) returns jsonb language plpgsql security definer set search_path = '' as $$
declare u uuid := ritmo_private.require_user(); p public.ritmo_profiles;
begin
 if p_nickname is null or p_nickname !~ '^[A-Za-z0-9_]{3,24}$' or p_enabled is null then
  raise exception 'invalid_profile' using errcode='22023';
 end if;
 insert into public.ritmo_profiles(user_id,nickname,ranking_enabled,enabled_at)
 values(u,p_nickname,p_enabled,case when p_enabled then now() end)
 on conflict(user_id) do update set nickname=excluded.nickname, ranking_enabled=excluded.ranking_enabled,
 enabled_at=case when excluded.ranking_enabled and not public.ritmo_profiles.ranking_enabled then now() else public.ritmo_profiles.enabled_at end
 returning * into p;
 return to_jsonb(p);
end $$;
revoke all on function ritmo_private.profile(text,boolean) from public, anon;
grant execute on function ritmo_private.profile(text,boolean) to authenticated;
create function public.ritmo_save_profile(p_nickname text,p_enabled boolean) returns jsonb language sql security invoker set search_path='' as $$select ritmo_private.profile(p_nickname,p_enabled)$$;
revoke all on function public.ritmo_save_profile(text,boolean) from public,anon;
grant execute on function public.ritmo_save_profile(text,boolean) to authenticated;

create function ritmo_private.submit(p_event_id uuid,p_started_at timestamptz,p_completed_at timestamptz) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid := ritmo_private.require_user(); p public.ritmo_profiles; existing public.ritmo_workout_events;
 d date; count_day integer; awarded integer; total bigint;
begin
 -- Lock this account for the entire transaction: simultaneous devices cannot exceed 125 XP/day.
 select * into p from public.ritmo_profiles where user_id=u for update;
 if not found then raise exception 'profile_required' using errcode='22023'; end if;
 if p_event_id is null then raise exception 'invalid_event' using errcode='22023'; end if;
 select * into existing from public.ritmo_workout_events where event_id=p_event_id;
 if found then
  if existing.user_id<>u then raise exception 'invalid_event' using errcode='22023'; end if;
  select coalesce(sum(xp),0) into total from public.ritmo_xp_ledger where user_id=u;
  return jsonb_build_object('xp',(select xp from public.ritmo_xp_ledger where event_id=p_event_id),'total_xp',total,'duplicate',true);
 end if;
 if not p.ranking_enabled then raise exception 'ranking_disabled' using errcode='22023'; end if;
 if p_started_at is null or p_completed_at is null or p_started_at < p.enabled_at or
    p_completed_at < p_started_at + interval '60 seconds' or p_completed_at > now() + interval '2 minutes' or
    p_completed_at < now() - interval '7 days' or p_completed_at > p_started_at + interval '24 hours' then
  raise exception 'invalid_time' using errcode='22023';
 end if;
 -- Day/week/month boundaries use UTC, the same for all participants.
 d := (p_completed_at at time zone 'UTC')::date;
 -- Overlapping declared sessions cannot earn duplicate XP on multiple devices.
 if exists(select 1 from public.ritmo_workout_events where user_id=u and started_at<p_completed_at and completed_at>p_started_at) then
  raise exception 'overlapping_event' using errcode='22023';
 end if;
 select count(*) into count_day from public.ritmo_workout_events where user_id=u and xp_day=d;
 awarded := case count_day when 0 then 100 when 1 then 25 else 0 end;
 insert into public.ritmo_workout_events(event_id,user_id,started_at,completed_at,xp_day) values(p_event_id,u,p_started_at,p_completed_at,d);
 insert into public.ritmo_xp_ledger(event_id,user_id,xp_day,xp) values(p_event_id,u,d,awarded);
 select coalesce(sum(xp),0) into total from public.ritmo_xp_ledger where user_id=u;
 return jsonb_build_object('xp',awarded,'total_xp',total,'duplicate',false);
end $$;
revoke all on function ritmo_private.submit(uuid,timestamptz,timestamptz) from public,anon;
grant execute on function ritmo_private.submit(uuid,timestamptz,timestamptz) to authenticated;
create function public.ritmo_submit_workout(p_event_id uuid,p_started_at timestamptz,p_completed_at timestamptz) returns jsonb language sql security invoker set search_path='' as $$select ritmo_private.submit(p_event_id,p_started_at,p_completed_at)$$;
revoke all on function public.ritmo_submit_workout(uuid,timestamptz,timestamptz) from public,anon;
grant execute on function public.ritmo_submit_workout(uuid,timestamptz,timestamptz) to authenticated;

-- Only this projection is public. No e-mail, dates of individual workouts or workout details.
create function ritmo_private.ranking(p_period text,p_limit integer) returns jsonb language plpgsql security definer set search_path='' as $$
declare since_day date; result jsonb;
begin
 if p_period not in ('weekly','monthly','all') or p_period is null or p_limit is null or p_limit not in (10,100) then
  raise exception 'invalid_ranking' using errcode='22023';
 end if;
 since_day := case p_period when 'weekly' then date_trunc('week',now() at time zone 'UTC')::date when 'monthly' then date_trunc('month',now() at time zone 'UTC')::date else '-infinity'::date end;
 with scores as (
  select p.user_id,p.nickname,coalesce(sum(l.xp) filter(where l.xp_day>=since_day),0)::bigint as xp,
   coalesce(sum(l.xp),0)::bigint as total_xp
  from public.ritmo_profiles p left join public.ritmo_xp_ledger l on l.user_id=p.user_id
  where p.ranking_enabled group by p.user_id,p.nickname
 ), ranked as (
  select row_number() over(order by xp desc,lower(nickname),user_id) as position,nickname,xp,(1+total_xp/500) as level,user_id from scores
 )
 select jsonb_build_object('entries',coalesce((select jsonb_agg(to_jsonb(t)-'user_id' order by t.position) from (select * from ranked where position<=p_limit) t),'[]'::jsonb),
  'me',(select to_jsonb(t)-'user_id' from ranked t where user_id=auth.uid()),'updated_at',now(),'period',p_period) into result;
 return result;
end $$;
revoke all on function ritmo_private.ranking(text,integer) from public;
grant execute on function ritmo_private.ranking(text,integer) to anon,authenticated;
create function public.ritmo_ranking(p_period text,p_limit integer) returns jsonb language sql security invoker set search_path='' as $$select ritmo_private.ranking(p_period,p_limit)$$;
revoke all on function public.ritmo_ranking(text,integer) from public;
grant execute on function public.ritmo_ranking(text,integer) to anon,authenticated;

create function ritmo_private.summary() returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid := ritmo_private.require_user(); result jsonb;
begin
 select jsonb_build_object('total_xp',coalesce(sum(xp),0),'today_xp',coalesce(sum(xp) filter(where xp_day=(now() at time zone 'UTC')::date),0),
 'active_days',count(distinct xp_day) filter(where xp>0),'rewarded_workouts',count(*) filter(where xp>0)) into result
 from public.ritmo_xp_ledger where user_id=u;
 return result;
end $$;
revoke all on function ritmo_private.summary() from public,anon;
grant execute on function ritmo_private.summary() to authenticated;
create function public.ritmo_my_summary() returns jsonb language sql security invoker set search_path='' as $$select ritmo_private.summary()$$;
revoke all on function public.ritmo_my_summary() from public,anon;
grant execute on function public.ritmo_my_summary() to authenticated;

create function ritmo_private.get_profile() returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid := ritmo_private.require_user(); result jsonb;
begin
 select to_jsonb(p) into result from public.ritmo_profiles p where user_id=u;
 return coalesce(result,'{}'::jsonb);
end $$;
revoke all on function ritmo_private.get_profile() from public,anon;
grant execute on function ritmo_private.get_profile() to authenticated;
create function public.ritmo_get_profile() returns jsonb language sql security invoker set search_path='' as $$select ritmo_private.get_profile()$$;
revoke all on function public.ritmo_get_profile() from public,anon;
grant execute on function public.ritmo_get_profile() to authenticated;
