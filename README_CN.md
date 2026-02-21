Fast MAVLink for Java（中文）

状态：当前为测试版，仍在积极开发中，欢迎 Issue 与 PR。

面向 Java 17+ 的高性能 MAVLink 2 解析与发送库，提供零分配快速路径。

特性
- 零分配快速解析与分发（acceptFast）
- MAVLink v1/v2 解析、CRC 校验、可选签名验证
- 自动生成 View/Dialect/pack 等辅助代码
- 高性能写包器（可选签名与 payload 裁剪）
- PayloadPool 线程内复用
- Netty 集成（解包器 + ByteBuf 写入辅助）
- Quarkus 集成（监听配置 + 注解分发 + 客户端）

Links
- English: [README.md](README.md)
- 发布指南: [PUBLISHING.md](PUBLISHING.md)
- MAVLink 协议: https://mavlink.io/en/
- MAVLink 消息定义: https://github.com/mavlink/mavlink/tree/master/message_definitions

快速开始

生成源码
1) Maven 插件（generate-sources 阶段）：
```xml
<plugin>
  <groupId>com.chulise</groupId>
  <artifactId>fast-mavlink-maven-plugin</artifactId>
  <version>1.0.0</version>
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

2) Gradle 插件：
```kotlin
plugins {
    id("com.chulise.mavlink.codegen") version "1.0.0"
}

mavlinkCodegen {
    xmlDir.set(layout.projectDirectory.dir("src/main/resources/xml"))
    xmlFiles.set(listOf("common.xml"))
    outputDir.set(layout.buildDirectory.dir("generated/sources/mavlink"))
}
```

3) 在项目根目录运行：
   - `mvn -q -DskipTests -pl mavlink-generator exec:java`
   - 或在 IDE 中运行 `com.chulise.mavlink.generator.GeneratorMain`

接收（快速路径）
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

发送（快速路径）
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

Netty（接收 + 发送）
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

Quarkus（application.properties）
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

Quarkus（application.yaml 列表）
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

Quarkus（监听 + 发送）
```java
@MavlinkListener("uav1")
public class TelemetryHandlers {
    @MavlinkSubscribe(HeartbeatView.class)
    public void onHeartbeat(HeartbeatView hb) {
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

Quarkus Demo（SITL 探针）
- Demo 模块：`mavlink-quarkus-demo`
- 验证目标：严格检查入站、出站与 `COMMAND_LONG -> COMMAND_ACK` 往返链路。
- 运行态状态机：
  - `PASS`：严格探针健康。
  - `FAIL`：严格探针转为不健康（tx/rx/ack 新鲜度超窗）。
  - `RECOVER`：失败后恢复健康。
- 启动模拟器：
  - `podman run -d --rm --name ardupilot-sitl-plane -p 5760:5760 -e VEHICLE=ArduPlane -e MODEL=plane radarku/ardupilot-sitl`
- 运行 demo：
  - `mvn -DskipTests -f mavlink-quarkus-demo/pom.xml quarkus:dev`
- 日志出现以下内容表示探针通过：
  - `Mavlink demo: first outbound heartbeat sent.`
  - `Mavlink demo: first COMMAND_ACK received, command=512, result=...`
  - `Mavlink demo probe: PASS (inbound + outbound + command ack are active).`
- 停止模拟器：
  - `podman stop ardupilot-sitl-plane`
- 自动化探针脚本（启动 SITL + 运行 demo + 可选断链恢复测试）：
  - `powershell -ExecutionPolicy Bypass -File mavlink-quarkus-demo/scripts/run-sitl-probe.ps1`

Quarkus 使用细节
- 依赖：在应用中引入 `fast-mavlink-quarkus` 和生成的消息模块。
- 监听类：使用 `@MavlinkListener("id")`，每个 `@MavlinkSubscribe` 方法必须只有一个参数，且为 `public`。
- 原始订阅：用 `@MavlinkSubscribe(raw = true)` 或参数类型 `MavlinkPacketView`。
- 客户端注入：`@Inject @MavlinkClientId("uav1") MavlinkClient`，或设置 `mavlink.client.default`；只有一个 listener 时直接 `@Inject MavlinkClient` 即可。
- 传输：`udp`、`tcp`（客户端模式，需要 `remote`）、`tcp-server`（服务端模式；send() 发给最后活动连接）。
- 请求匹配：可配置 `mavlink.listener.<id>.request.default-match`，或按请求使用 `MavlinkResponseMatchers.commandAck(...) / paramId(...) / paramIndex(...) / missionSeq(...)`。
- Dialect：严格校验时配置 `mavlink.listener.<id>.dialect=common`。

Quarkus 原生编译
原生兼容由编译期注解处理器生成注册器（不依赖反射）。
请确保启用注解处理，且 `@MavlinkSubscribe` 方法为 `public`。
Gradle 用户需要把处理器加到 `annotationProcessor` 配置。
若注解处理被禁用，运行时会回退到反射路径，需要手动注册反射。
回退路径的最小示例（reflect-config.json）：
```json
[
  {"name":"com.example.TelemetryHandlers","allDeclaredConstructors":true,"allDeclaredMethods":true},
  {"name":"com.chulise.mavlink.messages.HeartbeatView","allDeclaredConstructors":true},
  {"name":"com.chulise.mavlink.messages.CommandAckView","allDeclaredConstructors":true},
  {"name":"com.chulise.mavlink.messages.CommandLongView","allDeclaredConstructors":true}
]
```
或在监听类上使用 Quarkus 的 `@RegisterForReflection` 并显式列出你使用的 View 类。
如果 `mavlink.listener.<id>.dialect` 配置为类名（非 `common`），原生模式下请为该 Dialect 注册反射，或避免在原生中使用。

严格校验选项
```java
MavlinkParser.Options opts = MavlinkParser.Options.builder()
        .strict(true)
        .allowUnknown(true)
        .requireSignature(true)
        .requireSigned(true)
        .secretKey(secretKey)
        .signatureWindow(1000) // 重放窗口（可选）
        .messageSpecProvider(provider) // 扩展 CRC 表（可选）
        .build();
```

注意事项
- `acceptFast` 会复用线程内 view，不要在回调外保留引用。
- `PayloadPool` 提供线程内复用，`reset()` 自动调用。
- 若需要校验未知消息，请提供 `MessageSpecProvider`。
