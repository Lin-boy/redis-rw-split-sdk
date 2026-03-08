# Redis Read/Write Splitting SDK Starter

A high-performance Spring Boot Starter for automatic Redis Read/Write splitting. It routes write operations to a master Redis instance and read operations to multiple slave instances with lock-free load balancing.

## ✨ Features
- **Automatic Routing**: Automatically identifies read/write operations based on command names.
- **Manual Override**: ThreadLocal context (Virtual Thread friendly) to force routing to master or slaves.
- **High-Concurrency Optimized**: Uses **Lock-Free** load balancing (`ThreadLocalRandom`) to eliminate CAS contention in ultra-high throughput scenarios.
- **JDK 21+ Ready**: Full support for **Virtual Threads**. Designed to minimize memory footprint and prepared for `ScopedValue`.
- **Zero Configuration**: Fully integrated with Spring Boot Auto-configuration.
- **Extensible**: Built using Proxy and Factory patterns for easy integration with Lettuce or Jedis.

## 🚀 Performance for JDK 21 & High Concurrency
This SDK is specifically tuned for modern Java environments:
1. **ThreadLocalRandom**: Replaced `AtomicInteger` with `ThreadLocalRandom` for slave selection, providing near-linear scalability as CPU cores increase.
2. **Virtual Thread Safety**: `RWContextHolder` is optimized for the lifecycle of Virtual Threads, ensuring no memory leaks and consistent routing.
3. **Smart Defaults**: Defaults to `WRITE` (Master) routing if the context is lost (e.g., during async handovers), prioritizing data consistency.

## 📦 Installation
Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>redis-rw-split-sdk-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## ⚙️ Configuration
Add the following to your `application.yml`:

```yaml
spring:
  redis:
    rw:
      enabled: true
      master:
        host: localhost
        port: 6379
      slaves:
        - host: 192.168.1.10
          port: 6379
        - host: 192.168.1.11
          port: 6379
```

## 💡 Usage

### Automatic Routing
Standard `RedisTemplate` or `StringRedisTemplate` will automatically route commands:

```java
@Autowired
private StringRedisTemplate redisTemplate;

public void demo() {
    // Routes to Master (SET is a write operation)
    redisTemplate.opsForValue().set("key", "value");
    
    // Routes to Slaves (GET is a read operation, load balanced via ThreadLocalRandom)
    String val = redisTemplate.opsForValue().get("key");
}
```

### Manual Routing Override
Use `RWContextHolder` to manually specify routing. **Always use try-finally** to ensure context is cleared, especially when using Virtual Threads or Thread Pools.

```java
try {
    RWContextHolder.setRWType(RWType.WRITE);
    // This GET will be forced to Master for read-after-write consistency
    String val = redisTemplate.opsForValue().get("key");
} finally {
    RWContextHolder.clear();
}
```

## 🧪 Testing & Validation
The SDK includes a high-concurrency stress test that automatically detects your JDK version:

```bash
# Runs stress tests with 2000+ concurrent tasks
# Uses Virtual Threads if running on JDK 21+
mvn test -Dtest=RedisRWHighConcurrencyTest
```

## 🛠️ Internal Architecture

### Lock-Free Load Balancing
In high-concurrency scenarios, traditional `AtomicInteger` based Round-Robin suffers from cache line contention. Our implementation uses:
```java
int index = ThreadLocalRandom.current().nextInt(slaveCount);
return slaveFactories.get(index);
```
This ensures zero-contention even with thousands of concurrent virtual threads.

### Routing Logic Priority
1. **Explicit Context**: Checked first via `RWContextHolder`.
2. **Command Analysis**: If no context, the SDK analyzes the Redis command name (e.g., `GET`, `HGET` -> READ; `SET`, `DEL` -> WRITE).
3. **Safe Fallback**: Defaults to `MASTER` to prevent data loss or inconsistency.

## 🛣️ Roadmap
- [ ] **AOP Support**: `@Read` and `@Write` annotations for easier context management.
- [ ] **Health Checks**: Automatic removal of failed slave nodes.
- [ ] **ScopedValue Integration**: Transition from `ThreadLocal` to `ScopedValue` when JDK 21 is detected.
