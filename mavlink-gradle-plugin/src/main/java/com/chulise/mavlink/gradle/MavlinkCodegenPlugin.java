package com.chulise.mavlink.gradle;

import java.util.Collections;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;

public class MavlinkCodegenPlugin implements Plugin<Project>
{
    @Override
    public void apply(Project project)
    {
        MavlinkCodegenExtension ext = project.getExtensions()
                .create("mavlinkCodegen", MavlinkCodegenExtension.class, project.getObjects());

        ext.getXmlDir().convention(project.getLayout().getProjectDirectory().dir("src/main/resources/xml"));
        ext.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("generated/sources/mavlink"));
        ext.getXmlFiles().convention(Collections.singletonList("common.xml"));
        ext.getAddToSources().convention(true);

        TaskProvider<MavlinkGenerateTask> task = project.getTasks()
                .register("generateMavlink", MavlinkGenerateTask.class, t -> {
                    t.setGroup("mavlink");
                    t.setDescription("Generate MAVLink Java sources from XML definitions.");
                    t.getXmlDir().set(ext.getXmlDir());
                    t.getOutputDir().set(ext.getOutputDir());
                    t.getXmlFiles().set(ext.getXmlFiles());
                });

        project.getPlugins().withType(JavaPlugin.class, plugin -> project.afterEvaluate(p -> {
            if (ext.getAddToSources().getOrElse(true))
            {
                SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
                sourceSets.getByName("main").getJava().srcDir(ext.getOutputDir());
            }
            p.getTasks().named("compileJava").configure(t -> t.dependsOn(task));
        }));
    }
}
