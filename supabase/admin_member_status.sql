create or replace function public.admin_set_member_status(
  target_profile_id uuid,
  new_status public.member_status
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

  update public.profiles
  set
    member_status = new_status,
    approved_at = case
      when new_status = 'approved' then now()
      else approved_at
    end
  where id = target_profile_id;

  if not found then
    raise exception 'member profile not found';
  end if;
end;
$$;

grant execute on function public.admin_set_member_status(uuid, public.member_status)
to authenticated;
