package com.chulise.mavlink.gradle;

import com.chulise.mavlink.generator.MavlinkCodegen;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class MavlinkGenerateTask extends DefaultTask
{
    @InputDirectory
    public abstract DirectoryProperty getXmlDir();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Input
    public abstract ListProperty<String> getXmlFiles();

    @TaskAction
    public void generate()
    {
        File xmlDir = getXmlDir().getAsFile().get();
        File outputDir = getOutputDir().getAsFile().get();

        if (!xmlDir.exists() || !xmlDir.isDirectory())
        {
            throw new IllegalArgumentException("XML directory not found: " + xmlDir.getAbsolutePath());
        }

        if (!outputDir.exists() && !outputDir.mkdirs())
        {
            throw new IllegalStateException("Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        List<String> xmlFiles = getXmlFiles().getOrNull();
        if (xmlFiles == null || xmlFiles.isEmpty())
        {
            xmlFiles = new ArrayList<>();
            xmlFiles.add("common.xml");
        }

        MavlinkCodegen.run(xmlDir.toPath(), outputDir.toPath(), xmlFiles);
    }
}
