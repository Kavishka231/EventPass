package com.eventpass.seat;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class SeatLockService {
  private static final DefaultRedisScript<Long> RELEASE=new DefaultRedisScript<>("if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",Long.class);
  private final StringRedisTemplate redis; private final Duration ttl;
  public SeatLockService(StringRedisTemplate redis,@Value("${eventpass.booking.hold-ttl}")Duration ttl){this.redis=redis;this.ttl=ttl;}
  public boolean acquire(UUID eventId,UUID seatId,String owner){return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(eventId,seatId),owner,ttl));}
  public void release(UUID eventId,UUID seatId,String owner){redis.execute(RELEASE,List.of(key(eventId,seatId)),owner);}
  private String key(UUID event,UUID seat){return "seat-lock:"+event+":"+seat;}
}
