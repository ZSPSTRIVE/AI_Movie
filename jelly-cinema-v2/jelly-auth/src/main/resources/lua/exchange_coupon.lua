-- exchange_coupon.lua
-- KEYS[1]: stock key (jelly:growth:coupon:stock:{templateId})
-- KEYS[2]: users key (jelly:growth:coupon:users:{templateId})
-- ARGV[1]: userId
-- Return: 1=success, -1=already exchanged, -2=no stock

local stockKey = KEYS[1]
local usersKey = KEYS[2]
local userId = ARGV[1]

-- Check if user already exchanged
if redis.call('SISMEMBER', usersKey, userId) == 1 then
    return -1
end

-- Check and decrement stock
local stock = tonumber(redis.call('GET', stockKey) or 0)
if stock <= 0 then
    return -2
end

-- Decrement stock and add user
redis.call('DECR', stockKey)
redis.call('SADD', usersKey, userId)

return 1
