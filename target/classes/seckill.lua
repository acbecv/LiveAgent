-- 1.参数列表
-- 1.1优惠卷id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]

-- 2.数据key
-- 2.1库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2订单key 作为集合保存购买此优惠券用户id
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1判断库存是否充足
local stock = redis.call('get',stockKey)
if(not stock or tonumber(stock) <= 0)then
    -- 3.2 库存不足 返回1
    return 1
end
--3.2判断用户是否下单
if(redis.call('sismember',orderKey,userId) == 1) then
    -- 3.3存在,说明是重复下单
    return 2
end
-- 3.4扣库存
redis.call('incrby',stockKey,-1)
-- 3.5下单并保存用户
redis.call('sadd',orderKey,userId)
return 0


-- ============================================
-- 【扩展】一人五单Lua脚本（已注释，如需使用请取消注释）
-- ============================================
-- 说明：以下脚本实现了一人最多购买5单的功能
-- 使用方式：替换上面的脚本或创建新文件 seckill_five_per_user.lua
-- ============================================

--[[
-- 1.参数列表
-- 1.1优惠卷id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]
-- 1.3最大购买数量（默认为5）
local maxCount = tonumber(ARGV[3]) or 5

-- 2.数据key
-- 2.1库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2用户购买计数key（使用String类型存储购买数量）
local countKey = 'seckill:count:' .. voucherId .. ':' .. userId

-- 3.脚本业务
-- 3.1判断库存是否充足
local stock = redis.call('get', stockKey)
if(not stock or tonumber(stock) <= 0) then
    -- 3.2 库存不足 返回1
    return 1
end

-- 3.2获取用户已购买数量
local userCount = redis.call('get', countKey)
if(userCount and tonumber(userCount) >= maxCount) then
    -- 3.3 已达到购买上限 返回2
    return 2
end

-- 3.4扣库存
redis.call('decr', stockKey)

-- 3.5增加用户购买计数（原子操作）
local newCount = redis.call('incr', countKey)

-- 3.6如果是第一次购买，设置过期时间（24小时）
if(newCount == 1) then
    redis.call('expire', countKey, 86400)
end

-- 3.7返回成功，并返回当前购买数量
return newCount
--]]


-- ============================================
-- 【扩展】一人五单Lua脚本 - 使用Hash存储版本（已注释）
-- ============================================
-- 说明：使用Hash存储所有用户的购买数量，便于统一管理和查询
-- 优点：可以批量查询某个优惠券的所有用户购买情况
-- ============================================

--[[
-- 1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]
local maxCount = tonumber(ARGV[3]) or 5

-- 2.数据key
local stockKey = 'seckill:stock:' .. voucherId
-- 使用Hash存储：key=优惠券ID, field=用户ID, value=购买数量
local countHashKey = 'seckill:count:hash:' .. voucherId

-- 3.判断库存
local stock = redis.call('get', stockKey)
if(not stock or tonumber(stock) <= 0) then
    return 1  -- 库存不足
end

-- 4.获取用户已购买数量
local userCount = redis.call('hget', countHashKey, userId)
if(userCount and tonumber(userCount) >= maxCount) then
    return 2  -- 已达上限
end

-- 5.扣库存
redis.call('decr', stockKey)

-- 6.增加购买计数（Hash的hincrby是原子操作）
local newCount = redis.call('hincrby', countHashKey, userId, 1)

-- 7.设置Hash过期时间（只在第一次创建时设置）
local ttl = redis.call('ttl', countHashKey)
if(ttl == -1) then  -- -1表示没有设置过期时间
    redis.call('expire', countHashKey, 86400)
end

return newCount  -- 返回当前购买数量
--]]
