package com.chulise.mavlink.quarkus;

final class MavlinkListenerConfig
{
    final String id;
    final String transport;
    final String bind;
    final String remote;
    final String dialect;
    final MavlinkParserConfig parser;
    final MavlinkWriterConfig writer;
    final MavlinkRequestConfig request;

    MavlinkListenerConfig(String id,
                          String transport,
                          String bind,
                          String remote,
                          String dialect,
                          MavlinkParserConfig parser,
                          MavlinkWriterConfig writer,
                          MavlinkRequestConfig request)
    {
        this.id = id;
        this.transport = transport;
        this.bind = bind;
        this.remote = remote;
        this.dialect = dialect;
        this.parser = parser;
        this.writer = writer;
        this.request = request;
    }
}
