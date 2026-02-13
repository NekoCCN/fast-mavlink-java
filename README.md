Fast MAVLink for Java

High-performance MAVLink 2 parser and writer for Java 17+ with zero-allocation fast paths.

Features
- Zero-allocation fast-path parsing and dispatch (acceptFast)
- MAVLink v1/v2 parsing, CRC validation, optional signature verification
- Generated views, dialects, pack and writer helpers
- High-performance packet writer with optional signing and payload trimming
- PayloadPool for thread-local payload reuse
- Netty integration (packet decoder + ByteBuf writer helper)
- Quarkus integration (listener config + annotation dispatch + client)

Links
- 中文文档: [README_CN.md](README_CN.md)
- MAVLink Protocol: https://mavlink.io/en/
- MAVLink Message Definitions: https://github.com/mavlink/mavlink/tree/master/message_definitions

Quick Start

Generate Sources
1) Maven plugin (generate-sources phase):
```xml
<plugin>
  <groupId>com.chulise</groupId>
  <artifactId>fast-mavlink-maven-plugin</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <xmlDir>${project.basedir}/src/main/resources/xml</xmlDir>
    <xmlFiles>
      <xmlFile>common.xml</xmlFile>
    </xmlFiles>
    <outputDir>${project.build.directory}/generated-sources/mavlink</outputDir>
  </configuration>
</plugin>
```

2) Gradle plugin:
```kotlin
plugins {
    id("com.chulise.mavlink.codegen") version "1.0.0-SNAPSHOT"
}

mavlinkCodegen {
    xmlDir.set(layout.projectDirectory.dir("src/main/resources/xml"))
    xmlFiles.set(listOf("common.xml"))
    outputDir.set(layout.buildDirectory.dir("generated/sources/mavlink"))
}
```

3) Run the generator (from repo root):
   - `mvn -q -DskipTests -pl mavlink-generator exec:java`
   - Or run `com.chulise.mavlink.generator.GeneratorMain` from your IDE

Receive (fast path)
```java
ByteBuffer buffer = ...;
MavlinkParser.Options opts = MavlinkParser.Options.builder()
        .strict(true)
        .allowUnknown(false)
        .build();
MavlinkParser parser = new MavlinkParser(opts);
MavlinkParser.ParseResult res = parser.next(buffer, 0, new CommonDialect());
if (res != null) {
    CommonDialect dialect = new CommonDialect();
    dialect.acceptFast(res.view(), new CommonVisitor() {
        @Override
        public void visit(HeartbeatView hb) {
            System.out.println(hb.customMode());
        }
    });
}
```

Send (fast path)
```java
ByteBuffer out = ByteBuffer.allocate(512);
HeartbeatView.Payload p = HeartbeatView.PayloadPool.get();
p.customMode = 0x11223344L;
p.type = 1;
p.autopilot = 2;
p.baseMode = 3;
p.systemStatus = 4;
p.mavlinkVersion = 3;

MavlinkPacketWriter.Encoder encoder = MavlinkPacketWriter.builder()
        .sysId(1)
        .compId(1)
        .compatFlags(0)
        .incompatFlags(0)
        .trimExtensionZeros(true)
        .build();

int len = HeartbeatView.packV2(out, 0, encoder, 1, 1234L, p);
```

Netty (decode + send)
```java
pipeline.addLast(new MavlinkPacketDecoder(new MavlinkParser(opts), new CommonDialect()));
pipeline.addLast(new SimpleChannelInboundHandler<MavlinkPacket>() {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MavlinkPacket msg) {
        CommonDialect dialect = new CommonDialect();
        dialect.acceptFast(msg.view(), new CommonVisitor() {
            @Override
            public void visit(HeartbeatView hb) {
                System.out.println(hb.customMode());
            }
        });
    }
});

ByteBuf outBuf = ctx.alloc().buffer(MavlinkByteBufWriter.MAX_PACKET_LEN_V2);
HeartbeatView.Payload payload = HeartbeatView.PayloadPool.get();
int written = MavlinkByteBufWriter.write(outBuf, (buf, off) ->
        HeartbeatView.packV2(buf, off, encoder, 1, 1234L, payload));
ctx.writeAndFlush(outBuf);
```

Quarkus (application.properties)
```
mavlink.listeners=uav1

mavlink.listener.uav1.transport=udp
mavlink.listener.uav1.bind=0.0.0.0:14550
mavlink.listener.uav1.remote=192.168.1.10:14550
mavlink.listener.uav1.dialect=common

mavlink.listener.uav1.parser.strict=true
mavlink.listener.uav1.parser.allow-unknown=false
mavlink.listener.uav1.parser.require-signature=false
mavlink.listener.uav1.parser.signature-window=1000

mavlink.listener.uav1.writer.sys-id=1
mavlink.listener.uav1.writer.comp-id=1
mavlink.listener.uav1.writer.trim-extension-zeros=true
mavlink.listener.uav1.writer.link-id=1

mavlink.listener.uav1.request.default-match=msgid+sysid+compid+linkid
mavlink.listener.uav1.request.timeout-ms=500
mavlink.listener.uav1.request.expected-sys-id=1
mavlink.listener.uav1.request.expected-comp-id=1
```

Quarkus (application.yaml list style)
```yaml
mavlink:
  listeners:
    - id: uav1
      transport: udp
      bind: 0.0.0.0:14550
      remote: 192.168.1.10:14550
      dialect: common
      parser:
        strict: true
        allow-unknown: false
      writer:
        sys-id: 1
        comp-id: 1
      request:
        default-match: msgid+sysid+compid+linkid
        timeout-ms: 500
```

Quarkus (handler + send)
```java
@MavlinkListener("uav1")
public class TelemetryHandlers {
    @MavlinkSubscribe(HeartbeatView.class)
    void onHeartbeat(HeartbeatView hb) {
        System.out.println(hb.customMode());
    }
}

@Inject
@MavlinkClientId("uav1")
MavlinkClient client;

public void sendHeartbeat() {
    HeartbeatView.Payload payload = HeartbeatView.PayloadPool.get();
    client.send((buf, off) ->
            HeartbeatView.packV2(buf, off, client.encoder(), 1, 1234L, payload));
}

public void arm() {
    MavlinkRequestOptions opts = MavlinkRequestOptions.builder()
            .timeoutMs(500)
            .matcher(MavlinkResponseMatchers.commandAck(400))
            .build();
    CommandLongView.Payload payload = CommandLongView.PayloadPool.get();
    payload.targetSystem = 1;
    payload.targetComponent = 1;
    payload.command = 400;
    payload.confirmation = 0;
    client.request(CommandAckView.class, CommandAckView.ID, opts, (buf, off) ->
            CommandLongView.packV2(buf, off, client.encoder(), 1, 1234L, payload));
}
```

Quarkus Usage Details
- Dependency: add `fast-mavlink-quarkus` + generated messages module to your app.
- Listener classes: annotate with `@MavlinkListener("id")`, each `@MavlinkSubscribe` method must have exactly one parameter.
- Raw subscription: use `@MavlinkSubscribe(raw = true)` or parameter type `MavlinkPacketView`.
- Client injection: use `@Inject @MavlinkClientId("uav1") MavlinkClient` or set `mavlink.client.default`; if only one listener, plain `@Inject MavlinkClient` works.
- Transport: `udp`, `tcp` (client mode, requires `remote`), `tcp-server` (server mode; send() targets last active client).
- Request matching: set `mavlink.listener.<id>.request.default-match` or use `MavlinkResponseMatchers.commandAck(...) / paramId(...) / paramIndex(...) / missionSeq(...)` per request.
- Dialect: set `mavlink.listener.<id>.dialect=common` for strict CRC/length validation.

Quarkus Native Image
Native compatibility is handled by a build-time annotation processor that generates a registrar (no reflection).
Make sure annotation processing is enabled; `@MavlinkSubscribe` methods must be `public`.
If the processor is disabled, runtime falls back to reflection and you must register reflection manually.
Minimal example (reflect-config.json) for the fallback path:
```json
[
  {"name":"com.example.TelemetryHandlers","allDeclaredConstructors":true,"allDeclaredMethods":true},
  {"name":"com.chulise.mavlink.messages.HeartbeatView","allDeclaredConstructors":true},
  {"name":"com.chulise.mavlink.messages.CommandAckView","allDeclaredConstructors":true},
  {"name":"com.chulise.mavlink.messages.CommandLongView","allDeclaredConstructors":true}
]
```
Or annotate handler classes with Quarkus `@RegisterForReflection` and include view classes you use.
If you use `mavlink.listener.<id>.dialect` with a class name (not `common`), register that dialect class for reflection or avoid it in native builds.

Strict Validation Options
```java
MavlinkParser.Options opts = MavlinkParser.Options.builder()
        .strict(true)
        .allowUnknown(true)
        .requireSignature(true)
        .requireSigned(true)
        .secretKey(secretKey)
        .signatureWindow(1000) // optional replay window
        .messageSpecProvider(provider) // optional CRC table
        .build();
```

Notes
- `acceptFast` reuses per-thread views; do not retain references beyond the call.
- `PayloadPool` provides thread-local payload reuse; call `reset()` is automatic.
- To validate unknown messages, provide a `MessageSpecProvider` with CRC info.
