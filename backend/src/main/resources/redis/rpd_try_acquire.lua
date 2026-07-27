-- RPD try acquire. KEYS[1]=rl:rpd:{userId}:{date}
-- ARGV[1]=limit ARGV[2]=ttlSec
-- Returns: {0,n} blocked | {1,n} allowed
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local n = redis.call('INCR', key)
if n == 1 then
  redis.call('EXPIRE', key, ttl)
end
if n > limit then
  redis.call('DECR', key)
  return {0, n - 1}
end
return {1, n}
