-- Rollback one RPM member. KEYS[1]=rl:rpm:{userId} ARGV[1]=memberId
redis.call('ZREM', KEYS[1], ARGV[1])
return 1
