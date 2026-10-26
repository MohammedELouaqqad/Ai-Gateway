-- RPM sliding window (60s). KEYS[1]=rl:rpm:{userId}
-- ARGV[1]=nowMs ARGV[2]=windowMs ARGV[3]=limit ARGV[4]=memberId ARGV[5]=keyTtlSec
-- Returns: {0,count} blocked | {1,count} allowed
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]
local ttl = tonumber(ARGV[5])
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)
if count >= limit then
  return {0, count}
end
redis.call('ZADD', key, now, member)
redis.call('EXPIRE', key, ttl)
return {1, count + 1}
