if redis.call('HGET', KEYS[1], 'state') ~= 'VERIFIED' then return 0 end
if redis.call('HGET', KEYS[1], 'proofHash') ~= ARGV[1] then return 0 end
redis.call('DEL', KEYS[1])
return 1
