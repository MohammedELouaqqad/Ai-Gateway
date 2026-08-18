-- Peek RPM window size without adding. KEYS[1]=rl:rpm:{userId}
-- ARGV[1]=nowMs ARGV[2]=windowMs
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
return redis.call('ZCARD', key)
