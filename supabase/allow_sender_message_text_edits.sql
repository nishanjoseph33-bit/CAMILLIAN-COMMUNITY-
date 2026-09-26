-- Allow senders to edit message_text while protecting message identity,
-- media, creation time, and delivery/seen state.

create or replace function public.protect_message_content()
returns trigger
language plpgsql
as $function$
begin
  if new.id <> old.id
     or new.conversation_id <> old.conversation_id
     or new.sender_id <> old.sender_id
     or coalesce(new.media_url, '') <> coalesce(old.media_url, '')
     or new.created_at <> old.created_at then
    raise exception 'message content cannot be changed';
  end if;

  if old.delivered_at is not null
     and (new.delivered_at is null or new.delivered_at < old.delivered_at) then
    raise exception 'delivery status cannot move backwards';
  end if;

  if old.seen_at is not null
     and (new.seen_at is null or new.seen_at < old.seen_at) then
    raise exception 'seen status cannot move backwards';
  end if;

  if new.seen_at is not null and new.delivered_at is null then
    new.delivered_at := new.seen_at;
  end if;

  return new;
end;
$function$;