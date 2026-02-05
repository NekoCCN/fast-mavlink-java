package com.chulise.mavlink.generator;

import com.chulise.mavlink.generator.model.FieldDef;
import com.chulise.mavlink.generator.model.MessageDef;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutEngineTest
{

    @Test
    void testHeartbeatLayout()
    {
        MessageDef heartbeat = new MessageDef(0, "HEARTBEAT", "desc", List.of(
                new FieldDef("uint8_t", "type", "", false, 1),
                new FieldDef("uint8_t", "autopilot", "", false, 1),
                new FieldDef("uint8_t", "base_mode", "", false, 1),
                new FieldDef("uint32_t", "custom_mode", "", false, 1),
                new FieldDef("uint8_t", "system_status", "", false, 1),
                new FieldDef("uint8_t_mavlink_version", "mavlink_version", "", false, 1)
        ));

        LayoutEngine engine = new LayoutEngine();
        MessageLayout layout = engine.calculate(heartbeat);

        System.out.println("Calculated CRC: " + layout.crcExtra());
        assertEquals(50, layout.crcExtra(), "Heartbeat CRC should be 50");

        assertEquals(0, layout.offsets().get("custom_mode"), "custom_mode should be at offset 0");
        assertEquals(9, layout.lengthV1(), "Heartbeat length should be 9 bytes");
    }
}