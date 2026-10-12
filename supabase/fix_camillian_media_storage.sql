-- Fix post/profile images that upload successfully but cannot be fetched from publicUrl().
-- Run this script in the Supabase SQL Editor for the project's database.
-- The Camillian feed displays community posts publicly, so this bucket is intentionally public.
insert into storage.buckets (id, name, public)
values ('camillian-media', 'camillian-media', true)
on conflict (id) do update set public = true;

drop policy if exists "Camillian media is publicly readable" on storage.objects;
create policy "Camillian media is publicly readable"
on storage.objects
for select
to public
using (bucket_id = 'camillian-media');

drop policy if exists "Members upload their own Camillian media" on storage.objects;
create policy "Members upload their own Camillian media"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'camillian-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Members update their own Camillian media" on storage.objects;
create policy "Members update their own Camillian media"
on storage.objects
for update
to authenticated
using (
  bucket_id = 'camillian-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'camillian-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Members delete their own Camillian media" on storage.objects;
create policy "Members delete their own Camillian media"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'camillian-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);
