-- 私信只能通过带当前好友校验的 RPC 读取和发送。
-- 防止解除好友后绕过 list_friend_messages 直接查询历史消息。
revoke all on table public.friend_messages from anon;
revoke all on table public.friend_messages from authenticated;
