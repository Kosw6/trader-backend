-- Check every gate before any write; rejected requests cannot evict a valid challenge.
if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
if tonumber(redis.call('GET', KEYS[3]) or '0') >= 5 then return 0 end
if tonumber(redis.call('GET', KEYS[4]) or '0') >= 20 then return 0 end
if tonumber(redis.call('GET', KEYS[5]) or '0') >= 100 then return 0 end
for i = 3, 5 do
    if redis.call('INCR', KEYS[i]) == 1 then redis.call('EXPIRE', KEYS[i], 3600) end
end
redis.call('SET', KEYS[2], '1', 'EX', 60)
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], 'challenge', ARGV[1], 'codeMac', ARGV[2], 'attempts', '0', 'state', 'PENDING')
redis.call('EXPIRE', KEYS[1], 600)
return 1
