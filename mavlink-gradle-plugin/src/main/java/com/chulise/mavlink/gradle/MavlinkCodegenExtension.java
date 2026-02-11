package com.chulise.mavlink.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.file.DirectoryProperty;
import javax.inject.Inject;

public abstract class MavlinkCodegenExtension
{
    private final DirectoryProperty xmlDir;
    private final DirectoryProperty outputDir;
    private final ListProperty<String> xmlFiles;
    private final Property<Boolean> addToSources;

    @Inject
    public MavlinkCodegenExtension(ObjectFactory objects)
    {
        this.xmlDir = objects.directoryProperty();
        this.outputDir = objects.directoryProperty();
        this.xmlFiles = objects.listProperty(String.class);
        this.addToSources = objects.property(Boolean.class);
    }

    public DirectoryProperty getXmlDir()
    {
        return xmlDir;
    }

    public DirectoryProperty getOutputDir()
    {
        return outputDir;
    }

    public ListProperty<String> getXmlFiles()
    {
        return xmlFiles;
    }

    public Property<Boolean> getAddToSources()
    {
        return addToSources;
    }
}
