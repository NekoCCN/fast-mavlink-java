package com.chulise.mavlink.generator;

import com.chulise.mavlink.generator.model.MessageDef;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MavlinkCodegen
{
    public static void run(Path xmlDir, Path outputDir, List<String> xmlFilenames)
    {
        if (!xmlDir.toFile().exists())
        {
            throw new IllegalArgumentException("XML directory not found: " + xmlDir.toAbsolutePath());
        }

        XmlSchemaParser parser = new XmlSchemaParser(xmlDir);
        LayoutEngine layoutEngine = new LayoutEngine();
        MessageGenerator generator = new MessageGenerator(outputDir);
        DialectGenerator dialectGenerator = new DialectGenerator(outputDir);

        System.out.println(">>> Start Generating MAVLink Java Sources");
        System.out.println("    XML Dir: " + xmlDir);
        System.out.println("    Out Dir: " + outputDir);

        int totalGenerated = 0;

        for (String filename : xmlFilenames)
        {
            System.out.println("    Processing: " + filename);

            List<MessageDef> messages = parser.parse(filename);
            Map<Integer, String> classNames = resolveClassNames(messages);

            for (MessageDef msg : messages)
            {
                MessageLayout layout = layoutEngine.calculate(msg);
                generator.generate(msg, layout, classNames.get(msg.id()));
            }
            String dialectName = stripExtension(filename);
            dialectGenerator.generate(dialectName, messages, classNames);
            totalGenerated += messages.size();
        }

        System.out.println(">>> Finished. Generated " + totalGenerated + " classes.");
    }

    public static void run(Path xmlDir, Path outputDir, String xmlFilename)
    {
        run(xmlDir, outputDir, Collections.singletonList(xmlFilename));
    }

    private static String stripExtension(String filename)
    {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(0, idx) : filename;
    }

    private static Map<Integer, String> resolveClassNames(List<MessageDef> messages)
    {
        Map<Integer, String> classNamesById = new HashMap<>();
        Map<String, Integer> usedCaseInsensitive = new HashMap<>();
        for (MessageDef msg : messages)
        {
            String baseName = NameUtils.toClassName(msg.name());
            String key = baseName.toLowerCase(Locale.ROOT);
            String resolved = baseName;
            if (usedCaseInsensitive.containsKey(key))
            {
                String stem = baseName.endsWith("View")
                        ? baseName.substring(0, baseName.length() - "View".length())
                        : baseName;
                resolved = stem + "Msg" + msg.id() + "View";
                int suffix = 2;
                while (usedCaseInsensitive.containsKey(resolved.toLowerCase(Locale.ROOT)))
                {
                    resolved = stem + "Msg" + msg.id() + "V" + suffix + "View";
                    suffix++;
                }
                System.out.println("    Name collision detected for " + msg.name()
                        + ", using class name " + resolved);
            }
            classNamesById.put(msg.id(), resolved);
            usedCaseInsensitive.put(resolved.toLowerCase(Locale.ROOT), msg.id());
        }
        return classNamesById;
    }
}
