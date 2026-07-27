-- Budget reserve user+global (cents). KEYS[1]=user KEYS[2]=global
-- ARGV[1]=estimatedCents ARGV[2]=userCapCents ARGV[3]=globalCapCents ARGV[4]=ttlSec
-- Returns: {1,userAfter,globalAfter} ok | {0,1,userAfter,globalAfter} user blocked
--          {0,2,userAfter,globalAfter} global blocked
local userKey = KEYS[1]
local globalKey = KEYS[2]
local est = tonumber(ARGV[1])
local userCap = tonumber(ARGV[2])
local globalCap = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

local userAfter = redis.call('INCRBY', userKey, est)
if redis.call('TTL', userKey) < 0 then
  redis.call('EXPIRE', userKey, ttl)
end
local globalAfter = redis.call('INCRBY', globalKey, est)
if redis.call('TTL', globalKey) < 0 then
  redis.call('EXPIRE', globalKey, ttl)
end

if userAfter > userCap then
  redis.call('DECRBY', userKey, est)
  redis.call('DECRBY', globalKey, est)
  return {0, 1, userAfter - est, globalAfter - est}
end
if globalAfter > globalCap then
  redis.call('DECRBY', userKey, est)
  redis.call('DECRBY', globalKey, est)
  return {0, 2, userAfter - est, globalAfter - est}
end
return {1, userAfter, globalAfter}
