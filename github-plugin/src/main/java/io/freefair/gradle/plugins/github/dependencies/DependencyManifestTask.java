package io.freefair.gradle.plugins.github.dependencies;

import com.github.packageurl.PackageURLBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.freefair.gradle.plugins.github.internal.GitUtils;
import io.freefair.gradle.plugins.github.internal.Manifest;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DependencyManifestTask extends DefaultTask {

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<ResolvedComponentResult> getCompileClasspath();
    @Input
    public abstract Property<ResolvedComponentResult> getRuntimeClasspath();

    private transient Manifest manifest;

    @TaskAction
    public void writeManifest() throws IOException {

        manifest = new Manifest();

        manifest.setName(getProject().getPath());

        File gitDir = GitUtils.findWorkingDirectory(getProject());

        String filePath = getProject().getBuildFile().getCanonicalPath().replace(gitDir.getCanonicalPath(), "");
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }
        manifest.setFile(new Manifest.File(filePath));

        getCompileClasspath().get().getDependencies().stream()
                .filter(ResolvedDependencyResult.class::isInstance)
                .map(ResolvedDependencyResult.class::cast)
                .map(resolvedDependencyResult -> getGithubDependency(resolvedDependencyResult, "development"))
                .forEach(dep -> {
                    dep.setRelationship("direct");
                });

        getRuntimeClasspath().get().getDependencies().stream()
                .filter(ResolvedDependencyResult.class::isInstance)
                .map(ResolvedDependencyResult.class::cast)
                .map(d -> getGithubDependency(d, "runtime"))
                .forEach(dep -> {
                    dep.setRelationship("direct");
                });

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(manifest);
        Files.write(getOutputFile().getAsFile().get().toPath(), json.getBytes(StandardCharsets.UTF_8));

    }

    @NotNull
    private Manifest.Dependency getGithubDependency(ResolvedDependencyResult resolvedDependencyResult, String scope) {

        String packageUrl = getPackageUrl(resolvedDependencyResult.getSelected());

        Manifest.Dependency githubDep = manifest.getResolved().computeIfAbsent(packageUrl, Manifest.Dependency::new);

        githubDep.setRelationship("indirect");
        githubDep.setScope(scope);

        if (!resolvedDependencyResult.getRequested().getAttributes().toString().contains("org.gradle.category=platform")) {
            List<String> collect = resolvedDependencyResult.getSelected().getDependencies().stream()
                    .filter(ResolvedDependencyResult.class::isInstance)
                    .map(ResolvedDependencyResult.class::cast)
                    .map(d -> getGithubDependency(d, scope))
                    .map(Manifest.Dependency::getPackage_url)
                    .collect(Collectors.toList());

            githubDep.setDependencies(collect);
        }

        return githubDep;
    }

    @SneakyThrows
    private String getPackageUrl(ResolvedComponentResult selected) {
        return PackageURLBuilder.aPackageURL()
                .withType("maven")
                .withNamespace(selected.getModuleVersion().getGroup())
                .withName(selected.getModuleVersion().getName())
                .withVersion(selected.getModuleVersion().getVersion())
                .build()
                .toString();

    }

}