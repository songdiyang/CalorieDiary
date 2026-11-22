-- App 使用本地记录 ID 生成 client_ref，登录后重复上传时按用户去重。
alter table public.meal_entries
    add column if not exists client_ref text;

-- 允许旧记录和其他客户端保留 NULL；App 上传的非 NULL 值必须按用户唯一。
create unique index if not exists meal_entries_user_client_ref_idx
    on public.meal_entries(user_id, client_ref);

comment on column public.meal_entries.client_ref is
    '客户端幂等同步标识；App 格式为 android:<本地记录ID>。';
