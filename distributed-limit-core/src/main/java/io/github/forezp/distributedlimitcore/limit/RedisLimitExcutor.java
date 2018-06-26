package io.github.forezp.distributedlimitcore.limit;

import io.github.forezp.distributedlimitcore.entity.LimitEntity;
import io.github.forezp.distributedlimitcore.exception.LimitException;
import io.github.forezp.distributedlimitcore.util.IdentifierThreadLocal;
import io.github.forezp.distributedlimitcore.util.KeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Email miles02@163.com
 *
 * @author fangzhipeng
 * create 2018-06-26
 **/
public class RedisLimitExcutor implements LimitExcutor {

    private StringRedisTemplate stringRedisTemplate;
    Logger log = LoggerFactory.getLogger( RedisLimitExcutor.class );


    public RedisLimitExcutor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryAccess(LimitEntity limitEntity) {
        String identifier = limitEntity.getIdentifier();
        if (StringUtils.isEmpty( IdentifierThreadLocal.get() )) {
            identifier = IdentifierThreadLocal.get();
        }
        if (StringUtils.isEmpty( identifier )) {
            throw new LimitException( "identifier cannot be null" );
        }
        String key = limitEntity.getKey();
        if (StringUtils.isEmpty( key )) {
            throw new LimitException( "key cannot be null" );
        }

        int seconds = limitEntity.getSeconds();
        int limitCount = limitEntity.getLimtNum();
        String compositeKey = KeyUtil.compositeKey( identifier, key );
        List<String> keys = new ArrayList<>();
        keys.add( compositeKey );
        String luaScript = buildLuaScript();
        RedisScript<Long> redisScript = new DefaultRedisScript<>( luaScript, Long.class );
        Long count = stringRedisTemplate.execute( redisScript, keys, "" + limitCount, "" + seconds );
        log.info( "Access try count is {} for key={}", count, compositeKey );
        return count != 0;
    }

    private String buildLuaScript() {
        StringBuilder lua = new StringBuilder();
        lua.append( " local key = KEYS[1]" );
        lua.append( "\nlocal limit = tonumber(ARGV[1])" );
        lua.append( "\nlocal curentLimit = tonumber(redis.call('get', key) or \"0\")" );
        lua.append( "\nif curentLimit + 1 > limit then" );
        lua.append( "\nreturn 0" );
        lua.append( "\nelse" );
        lua.append( "\n redis.call(\"INCRBY\", key, 1)" );
        lua.append( "\nredis.call(\"EXPIRE\", key, ARGV[2])" );
        lua.append( "\nreturn curentLimit + 1" );
        lua.append( "\nend" );
        return lua.toString();
    }
}
