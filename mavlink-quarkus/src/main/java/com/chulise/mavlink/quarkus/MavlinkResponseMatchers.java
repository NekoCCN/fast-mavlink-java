package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkPacketView;
import com.chulise.mavlink.messages.CommandAckView;
import com.chulise.mavlink.messages.MissionItemIntView;
import com.chulise.mavlink.messages.MissionItemView;
import com.chulise.mavlink.messages.ParamValueView;
import java.nio.charset.StandardCharsets;

public final class MavlinkResponseMatchers
{
    private static final MavlinkResponseMatcher MESSAGE_ID_ONLY = (packet, req) -> true;

    private static final MavlinkResponseMatcher SYS_COMP = (packet, req) ->
    {
        if (req.expectedSysId >= 0 && packet.getSysId() != req.expectedSysId)
        {
            return false;
        }
        if (req.expectedCompId >= 0 && packet.getCompId() != req.expectedCompId)
        {
            return false;
        }
        return true;
    };

    private static final MavlinkResponseMatcher SYS_COMP_LINK = (packet, req) ->
    {
        if (!SYS_COMP.matches(packet, req))
        {
            return false;
        }
        if (req.expectedLinkId >= 0)
        {
            int linkId = packet.hasSignature() ? packet.getSignatureLinkId() : -1;
            return linkId == req.expectedLinkId;
        }
        return true;
    };

    private static final ThreadLocal<CommandAckView> COMMAND_ACK_VIEW = ThreadLocal.withInitial(CommandAckView::new);
    private static final ThreadLocal<ParamValueView> PARAM_VALUE_VIEW = ThreadLocal.withInitial(ParamValueView::new);
    private static final ThreadLocal<MissionItemView> MISSION_ITEM_VIEW = ThreadLocal.withInitial(MissionItemView::new);
    private static final ThreadLocal<MissionItemIntView> MISSION_ITEM_INT_VIEW = ThreadLocal.withInitial(MissionItemIntView::new);

    private MavlinkResponseMatchers()
    {
    }

    static MavlinkResponseMatcher fromConfig(String value)
    {
        if (value == null || value.isBlank())
        {
            return SYS_COMP_LINK;
        }

        String v = value.trim().toLowerCase();
        if ("msgid".equals(v) || "messageid".equals(v))
        {
            return MESSAGE_ID_ONLY;
        }
        if ("msgid+sysid+compid".equals(v) || "messageid+sysid+compid".equals(v))
        {
            return SYS_COMP;
        }
        if ("msgid+sysid+compid+linkid".equals(v) || "messageid+sysid+compid+linkid".equals(v))
        {
            return SYS_COMP_LINK;
        }
        return SYS_COMP_LINK;
    }

    public static MavlinkResponseMatcher commandAck(int command)
    {
        return (packet, req) ->
        {
            if (!SYS_COMP_LINK.matches(packet, req))
            {
                return false;
            }
            if (packet.getMessageId() != CommandAckView.ID)
            {
                return false;
            }
            CommandAckView view = COMMAND_ACK_VIEW.get();
            view.wrap(packet);
            return view.command() == command;
        };
    }

    public static MavlinkResponseMatcher paramId(String paramId)
    {
        return paramId(normalizeParamId(paramId));
    }

    public static MavlinkResponseMatcher paramId(byte[] paramId)
    {
        byte[] key = paramId == null ? new byte[16] : paramId;
        return (packet, req) ->
        {
            if (!SYS_COMP_LINK.matches(packet, req))
            {
                return false;
            }
            if (packet.getMessageId() != ParamValueView.ID)
            {
                return false;
            }
            ParamValueView view = PARAM_VALUE_VIEW.get();
            view.wrap(packet);
            for (int i = 0; i < 16; i++)
            {
                if (view.paramId(i) != (key[i] & 0xFF))
                {
                    return false;
                }
            }
            return true;
        };
    }

    public static MavlinkResponseMatcher paramIndex(int index)
    {
        return (packet, req) ->
        {
            if (!SYS_COMP_LINK.matches(packet, req))
            {
                return false;
            }
            if (packet.getMessageId() != ParamValueView.ID)
            {
                return false;
            }
            ParamValueView view = PARAM_VALUE_VIEW.get();
            view.wrap(packet);
            return view.paramIndex() == index;
        };
    }

    public static MavlinkResponseMatcher missionSeq(int seq)
    {
        return (packet, req) ->
        {
            if (!SYS_COMP_LINK.matches(packet, req))
            {
                return false;
            }
            int id = packet.getMessageId();
            if (id == MissionItemView.ID)
            {
                MissionItemView view = MISSION_ITEM_VIEW.get();
                view.wrap(packet);
                return view.seq() == seq;
            }
            if (id == MissionItemIntView.ID)
            {
                MissionItemIntView view = MISSION_ITEM_INT_VIEW.get();
                view.wrap(packet);
                return view.seq() == seq;
            }
            return false;
        };
    }

    private static byte[] normalizeParamId(String paramId)
    {
        byte[] key = new byte[16];
        if (paramId == null)
        {
            return key;
        }
        byte[] src = paramId.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(src.length, 16);
        System.arraycopy(src, 0, key, 0, len);
        return key;
    }
}
