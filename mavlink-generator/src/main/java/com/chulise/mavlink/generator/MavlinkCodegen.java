package com.chulise.mavlink.generator;

import com.chulise.mavlink.generator.model.MessageDef;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

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

            for (MessageDef msg : messages)
            {
                MessageLayout layout = layoutEngine.calculate(msg);
                generator.generate(msg, layout);
            }
            String dialectName = stripExtension(filename);
            dialectGenerator.generate(dialectName, messages);
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
}
