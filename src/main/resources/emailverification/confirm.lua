if redis.call('HGET', KEYS[1], 'state') ~= 'PENDING' then return 0 end
if redis.call('HGET', KEYS[1], 'challenge') ~= ARGV[1] then return 0 end
local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if attempts > 5 then return 0 end
if redis.call('HGET', KEYS[1], 'codeMac') ~= ARGV[2] then
    if attempts == 5 then redis.call('DEL', KEYS[1]) end
    return 0
end
redis.call('HDEL', KEYS[1], 'codeMac', 'attempts')
redis.call('HSET', KEYS[1], 'state', 'VERIFIED', 'proofHash', ARGV[3])
redis.call('EXPIRE', KEYS[1], 600)
return 1
