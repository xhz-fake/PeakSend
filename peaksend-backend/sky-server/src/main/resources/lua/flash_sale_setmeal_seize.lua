
-- 这个 Lua 文件本质上就是一段“在 Redis 里面执行的抢购规则代码”。
-- Lua 脚本在 Redis 里执行时是 原子的, 这整段脚本执行时，中间不会被别的请求插进来打断
local activityKey = KEYS[1]
local userSetKey = KEYS[2]
-- KEYS：Redis 的 key

local userId = ARGV[1]
local now = tonumber(ARGV[2])
-- ARGV：普通参数

--[[ 这几行的意思就是：
  - activityKey ：这场活动的 Redis Hash
  - userSetKey ：这场活动的已抢用户集合
  - userId ：当前抢购用户
  - now ：当前时间
  ]]

if redis.call('EXISTS', activityKey) == 0 then
    return -1
end

local status = tonumber(redis.call('HGET', activityKey, 'status'))
local stock = tonumber(redis.call('HGET', activityKey, 'stock'))
local startTime = tonumber(redis.call('HGET', activityKey, 'startTime'))
local endTime = tonumber(redis.call('HGET', activityKey, 'endTime'))
--[[在 Redis 的 Hash 里读活动关键字段：

    - 状态
    - 库存
    - 开始时间
    - 结束时间
    ]]

if status == nil or stock == nil or startTime == nil or endTime == nil then
    return -1
end
--防 Redis 数据不完整。

if status ~= 1 then
    return -2
end

if now < startTime then
    return -3
end

if now > endTime then
    return -4
end

if redis.call('SISMEMBER', userSetKey, userId) == 1 then
    return -5
end
-- 看 userSetKey 这个 Set 里有没有当前 userId

if stock <= 0 then
    return -6
end

redis.call('HINCRBY', activityKey, 'stock', -1)
-- 活动库存减 1
redis.call('SADD', userSetKey, userId)
-- 把当前用户放进“已抢购用户集合”

return 1
-- 告诉 Java：这次 Redis 抢资格成功了
