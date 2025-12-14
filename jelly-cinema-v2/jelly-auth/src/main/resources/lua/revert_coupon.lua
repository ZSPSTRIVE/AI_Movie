-- revert_coupon.lua
-- KEYS[1]: stock key (jelly:growth:coupon:stock:{templateId})
-- KEYS[2]: users key (jelly:growth:coupon:users:{templateId})
-- ARGV[1]: userId
-- Return: 1=success

local stockKey = KEYS[1]
local usersKey = KEYS[2]
local userId = ARGV[1]

-- Revert: increment stock and remove user
redis.call('INCR', stockKey)
redis.call('SREM', usersKey, userId)

return 1
