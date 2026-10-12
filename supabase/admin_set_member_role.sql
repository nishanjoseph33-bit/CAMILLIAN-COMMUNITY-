-- Allows an existing administrator to grant or revoke the admin role from approved members.
-- Access is checked server-side; clients cannot directly update member_role through this RPC.
create or replace function public.admin_set_member_role(
  target_profile_id uuid,
  new_role text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception 'not authorized';
  end if;

  if new_role not in ('member', 'admin') then
    raise exception 'invalid member role';
  end if;

  if target_profile_id = auth.uid() then
    raise exception 'you cannot change your own administrator role';
  end if;

  if not exists (
    select 1 from public.profiles
    where id = target_profile_id and member_status = 'approved'
  ) then
    raise exception 'only approved members can be assigned administrator roles';
  end if;

  update public.profiles
  set member_role = new_role
  where id = target_profile_id
    and member_role <> 'super_admin';

  if not found then
    raise exception 'member not found or protected super administrator';
  end if;
end;
$$;

grant execute on function public.admin_set_member_role(uuid, text) to authenticated;
