package com.chulise.mavlink.generator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class GeneratorMain
{
    public static void main(String[] args)
    {
        Path xmlDir = Paths.get("src/main/resources/xml");

        Path outputDir = Paths.get("../mavlink-core/src/main/java");

        String targetXml = "common.xml";

        if (!xmlDir.toFile().exists())
        {
            xmlDir = Paths.get("mavlink-generator/src/main/resources/xml");
        }

        try
        {
            MavlinkCodegen.run(xmlDir, outputDir, targetXml);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}