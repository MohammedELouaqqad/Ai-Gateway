-- Release or adjust reservation. KEYS[1]=user KEYS[2]=global ARGV[1]=deltaCents (can be negative)
local d = tonumber(ARGV[1])
if d == 0 then
  return 1
end
redis.call('INCRBY', KEYS[1], d)
redis.call('INCRBY', KEYS[2], d)
return 1
