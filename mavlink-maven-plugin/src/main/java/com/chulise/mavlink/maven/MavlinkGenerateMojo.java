package com.chulise.mavlink.maven;

import com.chulise.mavlink.generator.MavlinkCodegen;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class MavlinkGenerateMojo extends AbstractMojo
{
    @Parameter(property = "mavlink.codegen.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "mavlink.codegen.xmlDir", defaultValue = "${project.basedir}/src/main/resources/xml")
    private File xmlDir;

    @Parameter(property = "mavlink.codegen.outputDir", defaultValue = "${project.build.directory}/generated-sources/mavlink")
    private File outputDir;

    @Parameter
    private List<String> xmlFiles;

    @Parameter(property = "mavlink.codegen.addToSources", defaultValue = "true")
    private boolean addToSources;

    @Parameter(property = "mavlink.codegen.failOnMissing", defaultValue = "true")
    private boolean failOnMissing;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException
    {
        if (skip)
        {
            getLog().info("MAVLink codegen skipped.");
            return;
        }

        if (xmlFiles == null || xmlFiles.isEmpty())
        {
            xmlFiles = new ArrayList<>();
            xmlFiles.add("common.xml");
        }

        if (xmlDir == null || !xmlDir.exists())
        {
            String msg = "XML directory not found: " + (xmlDir == null ? "null" : xmlDir.getAbsolutePath());
            if (failOnMissing)
            {
                throw new MojoExecutionException(msg);
            }
            getLog().warn(msg);
            return;
        }

        if (!outputDir.exists() && !outputDir.mkdirs())
        {
            throw new MojoExecutionException("Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        try
        {
            Path xmlPath = xmlDir.toPath();
            Path outPath = outputDir.toPath();
            getLog().info("Generating MAVLink sources...");
            getLog().info("XML Dir: " + xmlPath.toAbsolutePath());
            getLog().info("Out Dir: " + outPath.toAbsolutePath());
            getLog().info("XML Files: " + xmlFiles);
            MavlinkCodegen.run(xmlPath, outPath, xmlFiles);
        } catch (Exception e)
        {
            throw new MojoExecutionException("MAVLink codegen failed.", e);
        }

        if (addToSources)
        {
            project.addCompileSourceRoot(outputDir.getAbsolutePath());
        }
    }
}
