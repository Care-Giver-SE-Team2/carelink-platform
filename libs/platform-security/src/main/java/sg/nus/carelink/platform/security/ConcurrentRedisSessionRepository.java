package sg.nus.carelink.platform.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionMapper;
import org.springframework.util.Assert;

/**
 * The default Spring Session Redis hash format, with conditional, atomic saves. An existing
 * request must never recreate a session that another request has logged out. Only a newly
 * created session may create a key; attribute deltas preserve other requests' unrelated changes.
 * Redis/serialization errors deliberately propagate rather than pretending a logout succeeded.
 */
public final class ConcurrentRedisSessionRepository
		implements SessionRepository<ConcurrentRedisSessionRepository.RedisSession> {

	private static final String CREATED = "creationTime";
	private static final String ACCESSED = "lastAccessedTime";
	private static final String INTERVAL = "maxInactiveInterval";
	private static final String ATTRIBUTE = "sessionAttr:";
	private static final List<String> METADATA = List.of(CREATED, ACCESSED, INTERVAL);

	// Checking, applying deltas, rotating an ID and setting expiry are one Redis operation.
	// A rejected existing save is a no-op: an already-authorized read may finish, but its
	// snapshot cannot restore authentication for any subsequent request.
	private static final byte[] SAVE = bytes("""
			if ARGV[1] == '1' then
			  if redis.call('EXISTS', KEYS[2]) == 1 then
			    return redis.error_reply('Session ID collision')
			  end
			else
			  if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
			  if redis.call('HEXISTS', KEYS[1], 'creationTime') == 0
			    or redis.call('HEXISTS', KEYS[1], 'lastAccessedTime') == 0
			    or redis.call('HEXISTS', KEYS[1], 'maxInactiveInterval') == 0 then
			    redis.call('DEL', KEYS[1]); return 0
			  end
			  if redis.call('HGET', KEYS[1], 'creationTime') ~= ARGV[3] then return 0 end
			  if KEYS[1] ~= KEYS[2] then
			    if redis.call('EXISTS', KEYS[2]) == 1 then
			      return redis.error_reply('Session ID collision')
			    end
			    redis.call('RENAME', KEYS[1], KEYS[2])
			  end
			end
			for i = 1, tonumber(ARGV[4]) do
			  local p = 5 + (i - 1) * 3
			  if ARGV[p + 1] == '0' then redis.call('HDEL', KEYS[2], ARGV[p])
			  else redis.call('HSET', KEYS[2], ARGV[p], ARGV[p + 2]) end
			end
			if ARGV[2] == '-1' then redis.call('PERSIST', KEYS[2])
			else redis.call('PEXPIREAT', KEYS[2], ARGV[2]) end
			return 1
			""");

	// Do not delete a healthy/refreshed record that replaced the invalid snapshot we read.
	private static final byte[] DELETE_UNCHANGED = bytes("""
			for i = 1, 3 do
			  local p = 1 + (i - 1) * 3
			  local current = redis.call('HGET', KEYS[1], ARGV[p])
			  if ARGV[p + 1] == '0' then
			    if current then return 0 end
			  elseif current ~= ARGV[p + 2] then return 0 end
			end
			return redis.call('DEL', KEYS[1])
			""");

	private final RedisOperations<String, Object> redis;
	private final Supplier<? extends Session> factory;
	private final String keyPrefix;
	private final FlushMode flushMode;
	private final SaveMode saveMode;
	private final SessionIdGenerator idGenerator;
	private final RedisSessionMapper mapper = new RedisSessionMapper();

	public ConcurrentRedisSessionRepository(RedisOperations<String, Object> redis, Supplier<? extends Session> factory,
			String namespace, FlushMode flushMode, SaveMode saveMode, SessionIdGenerator idGenerator) {
		this.redis = redis;
		this.factory = factory;
		this.keyPrefix = namespace.trim() + ":sessions:";
		this.flushMode = flushMode;
		this.saveMode = saveMode;
		this.idGenerator = idGenerator;
	}

	@Override
	public RedisSession createSession() {
		RedisSession session = new RedisSession(new MapSession(factory.get()), true);
		session.flushIfRequired();
		return session;
	}

	@Override
	public RedisSession findById(String id) {
		Map<String, Object> fields = this.redis.<String, Object>opsForHash().entries(key(id));
		if (fields.isEmpty()) return null;
		// These are the exact required types in Spring Session's RedisSessionMapper.
		if (!(fields.get(CREATED) instanceof Long) || !(fields.get(ACCESSED) instanceof Long)
				|| !(fields.get(INTERVAL) instanceof Integer)) {
			deleteUnchanged(id, fields);
			return null;
		}
		MapSession session = this.mapper.apply(id, fields);
		if (session.isExpired()) {
			deleteUnchanged(id, fields);
			return null;
		}
		return new RedisSession(session, false);
	}

	@Override
	public void save(RedisSession session) {
		if (session.discarded) return;
		Map<String, Object> delta = new LinkedHashMap<>(session.delta);
		if (session.isNew || this.saveMode == SaveMode.ALWAYS) {
			for (String name : session.getAttributeNames()) delta.put(ATTRIBUTE + name, session.cached.getAttribute(name));
		}
		if (delta.isEmpty() && session.originalId.equals(session.getId())) return;
		long expires = session.getMaxInactiveInterval().isNegative() ? -1
				: session.getLastAccessedTime().plus(session.getMaxInactiveInterval()).toEpochMilli();
		List<byte[]> args = new ArrayList<>();
		args.add(bytes(session.isNew ? "1" : "0"));
		args.add(bytes(Long.toString(expires)));
		args.add(value(session.getCreationTime().toEpochMilli()));
		args.add(bytes(Integer.toString(delta.size())));
		delta.forEach((field, value) -> {
			args.add(hashKey(field));
			args.add(bytes(value == null ? "0" : "1"));
			args.add(value == null ? new byte[0] : value(value));
		});
		long saved = evaluate(SAVE, List.of(key(session.originalId), key(session.getId())), args);
		session.discarded = saved == 0;
		session.isNew = false;
		session.originalId = session.getId();
		session.delta.clear();
	}

	@Override
	public void deleteById(String id) {
		this.redis.delete(key(id));
	}

	private void deleteUnchanged(String id, Map<String, Object> fields) {
		List<byte[]> args = new ArrayList<>();
		for (String name : METADATA) {
			args.add(hashKey(name));
			args.add(bytes(fields.containsKey(name) ? "1" : "0"));
			args.add(fields.containsKey(name) ? value(fields.get(name)) : new byte[0]);
		}
		evaluate(DELETE_UNCHANGED, List.of(key(id)), args);
	}

	private long evaluate(byte[] script, List<String> keys, List<byte[]> args) {
		List<byte[]> parameters = new ArrayList<>();
		for (String key : keys) parameters.add(serialize(this.redis.getKeySerializer(), key));
		parameters.addAll(args);
		Long result = this.redis.execute((RedisCallback<Long>) connection ->
				connection.scriptingCommands().eval(script, ReturnType.INTEGER, keys.size(), parameters.toArray(byte[][]::new)));
		Assert.state(result != null, "Session script returned no result");
		return result;
	}

	private String key(String id) { return this.keyPrefix + id; }
	private byte[] value(Object value) { return serialize(this.redis.getHashValueSerializer(), value); }
	private byte[] hashKey(String name) { return serialize(this.redis.getHashKeySerializer(), name); }
	private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

	@SuppressWarnings("unchecked")
	private static byte[] serialize(RedisSerializer<?> serializer, Object value) {
		Assert.notNull(serializer, "Session Redis serializer must not be null");
		return ((RedisSerializer<Object>) serializer).serialize(value);
	}

	/** One request's snapshot and only the attributes that request changed. */
	public final class RedisSession implements Session {
		private final MapSession cached;
		private final String requestedId;
		private final Map<String, Object> delta = new LinkedHashMap<>();
		private String originalId;
		private boolean isNew;
		private boolean discarded;

		private RedisSession(MapSession cached, boolean isNew) {
			this.cached = cached;
			this.cached.setSessionIdGenerator(idGenerator);
			this.requestedId = cached.getId();
			this.originalId = cached.getId();
			this.isNew = isNew;
			if (isNew) {
				this.delta.put(CREATED, cached.getCreationTime().toEpochMilli());
				this.delta.put(ACCESSED, cached.getLastAccessedTime().toEpochMilli());
				this.delta.put(INTERVAL, Math.toIntExact(cached.getMaxInactiveInterval().getSeconds()));
			}
		}

		// A failed rotation must not advertise an unpersisted replacement ID to the HTTP filter.
		@Override public String getId() { return this.discarded ? this.requestedId : this.cached.getId(); }
		@Override public String changeSessionId() { String id = this.cached.changeSessionId(); flushIfRequired(); return this.discarded ? this.requestedId : id; }
		@Override public Instant getCreationTime() { return this.cached.getCreationTime(); }
		@Override public Instant getLastAccessedTime() { return this.cached.getLastAccessedTime(); }
		@Override public Duration getMaxInactiveInterval() { return this.cached.getMaxInactiveInterval(); }
		@Override public boolean isExpired() { return this.discarded || this.cached.isExpired(); }
		@Override public Set<String> getAttributeNames() { return this.cached.getAttributeNames(); }

		@Override public <T> T getAttribute(String name) {
			T value = this.cached.getAttribute(name);
			if (value != null && saveMode == SaveMode.ON_GET_ATTRIBUTE) this.delta.put(ATTRIBUTE + name, value);
			return value;
		}

		@Override public void setAttribute(String name, Object value) {
			this.cached.setAttribute(name, value);
			this.delta.put(ATTRIBUTE + name, value);
			flushIfRequired();
		}
		@Override public void removeAttribute(String name) { setAttribute(name, null); }

		@Override public void setLastAccessedTime(Instant time) {
			this.cached.setLastAccessedTime(time);
			this.delta.put(ACCESSED, time.toEpochMilli());
			flushIfRequired();
		}

		@Override public void setMaxInactiveInterval(Duration interval) {
			this.cached.setMaxInactiveInterval(interval);
			this.delta.put(INTERVAL, Math.toIntExact(interval.getSeconds()));
			flushIfRequired();
		}

		private void flushIfRequired() {
			if (flushMode == FlushMode.IMMEDIATE) save(this);
		}
	}
}
