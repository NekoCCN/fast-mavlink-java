package com.chulise.mavlink.generator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GeneratorMain
{
    public static void main(String[] args)
    {
        RunOptions options = parseArgs(args);
        Path xmlDir = options.xmlDir != null ? options.xmlDir : Paths.get("src/main/resources/xml");
        if (options.outputDir == null)
        {
            options.outputDir = Paths.get("../mavlink-common/src/main/java");
        }
        if (options.xmlFiles.isEmpty())
        {
            options.xmlFiles.add("common.xml");
        }
        if (!xmlDir.toFile().exists())
        {
            Path fallback = Paths.get("mavlink-generator/src/main/resources/xml");
            if (fallback.toFile().exists())
            {
                xmlDir = fallback;
            }
        }

        try
        {
            MavlinkCodegen.run(xmlDir, options.outputDir, options.xmlFiles);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static RunOptions parseArgs(String[] args)
    {
        RunOptions options = new RunOptions();
        int idx = 0;
        while (idx < args.length)
        {
            String arg = args[idx];
            switch (arg)
            {
                case "--help":
                case "-h":
                    printUsageAndExit();
                    break;
                case "--xml-dir":
                    options.xmlDir = readPathArg(args, idx, arg);
                    idx += 2;
                    break;
                case "--out-dir":
                    options.outputDir = readPathArg(args, idx, arg);
                    idx += 2;
                    break;
                case "--xml":
                    options.xmlFiles.add(readStringArg(args, idx, arg));
                    idx += 2;
                    break;
                default:
                    options.xmlFiles.add(arg);
                    idx += 1;
                    break;
            }
        }
        return options;
    }

    private static Path readPathArg(String[] args, int idx, String argName)
    {
        return Paths.get(readStringArg(args, idx, argName));
    }

    private static String readStringArg(String[] args, int idx, String argName)
    {
        int valueIdx = idx + 1;
        if (valueIdx >= args.length)
        {
            throw new IllegalArgumentException("Missing value for argument: " + argName);
        }
        return args[valueIdx];
    }

    private static void printUsageAndExit()
    {
        System.out.println("Usage:");
        System.out.println("  GeneratorMain [--xml-dir <dir>] [--out-dir <dir>] [--xml <file>]...");
        System.out.println("  GeneratorMain [xml1.xml xml2.xml ...]");
        System.exit(0);
    }

    private static final class RunOptions
    {
        private Path xmlDir;
        private Path outputDir;
        private final List<String> xmlFiles = new ArrayList<>();
    }
}
