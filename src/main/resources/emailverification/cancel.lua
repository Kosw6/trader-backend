-- A late send failure must not delete a newer request.
if redis.call('HGET', KEYS[1], 'challenge') ~= ARGV[1] then return 0 end
redis.call('DEL', KEYS[1])
return 1
