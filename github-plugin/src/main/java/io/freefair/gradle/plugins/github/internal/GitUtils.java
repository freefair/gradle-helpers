package io.freefair.gradle.plugins.github.internal;

import io.freefair.gradle.util.GitUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecOutput;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Lars Grefer
 */
@UtilityClass
@Slf4j
public class GitUtils {

    private static final Pattern httpsUrlPattern = Pattern.compile("https://github\\.com/(.+/.+)\\.git");
    private static final Pattern sshUrlPattern = Pattern.compile("git@github.com:(.+/.+)\\.git");

    @Nullable
    public static String findSlug(Project project) {

        if (GitUtil.isGithubActions(project.getProviders())) {
            return project.getProviders().environmentVariable("GITHUB_REPOSITORY").get();
        }

        String travisSlug = findTravisSlug(project).getOrNull();

        if (travisSlug != null && !travisSlug.isEmpty()) {
            return travisSlug;
        }

        String remoteUrl = getRemoteUrl(project, "origin");

        Matcher httpsMatcher = httpsUrlPattern.matcher(remoteUrl);
        if (httpsMatcher.matches()) {
            return httpsMatcher.group(1);
        }

        Matcher sshMatcher = sshUrlPattern.matcher(remoteUrl);
        if (sshMatcher.matches()) {
            return sshMatcher.group(1);
        }

        return null;
    }

    @Nonnull
    public Provider<String> findTravisSlug(Project project) {

        Provider<String> travisRepoSlug = project.getProviders().environmentVariable("TRAVIS_REPO_SLUG");

        ExecOutput execOutput = project.getProviders().exec(execSpec -> {
            execSpec.workingDir(project.getProjectDir());
            execSpec.commandLine("git", "config", "travis.slug");
            execSpec.setIgnoreExitValue(true);
        });

        return travisRepoSlug.orElse(execOutput.getStandardOutput().getAsText().map(String::trim));
    }

    public String getRemoteUrl(Project project, String remote) {
        return GitUtil.execute(project, "git", "ls-remote", "--get-url", remote).get();
    }

    public Provider<File> findWorkingDirectory(Project project) {

        Provider<String> gitToplevel = GitUtil.execute(project, "git", "rev-parse", "--show-toplevel");

        return project.getProviders().environmentVariable("TRAVIS_BUILD_DIR")
                .orElse(project.getProviders().environmentVariable("GITHUB_WORKSPACE"))
                .orElse(gitToplevel)
                .map(File::new);
    }

    public Provider<String> getTag(Project project) {

        Provider<String> travisTag = project.getProviders().environmentVariable("TRAVIS_TAG");

        Provider<String> githubTag = project.getProviders().environmentVariable("GITHUB_REF")
                .map(githubRef -> {
                    if (githubRef.startsWith("refs/tags/")) {
                        return githubRef.substring("refs/tags/".length());
                    }
                    else {
                        return null;
                    }
                });

        Provider<String> gitlabTag = project.getProviders().environmentVariable("CI_COMMIT_TAG");

        return travisTag
                .orElse(githubTag)
                .orElse(gitlabTag)
                .orElse(GitUtil.execute(project, "git", "tag", "--points-at", "HEAD"))
                .orElse("HEAD");

    }

    @Nonnull
    public Provider<String> findGithubUsername(Project project) {
        return project.getProviders().environmentVariable("GITHUB_ACTOR")
                .orElse(project.getProviders().gradleProperty("githubUsername"));
    }

    @Nonnull
    public Provider<String> findGithubToken(Project project) {
        return project.getProviders().environmentVariable("GITHUB_TOKEN")
                .orElse(project.getProviders().gradleProperty("githubToken"));
    }
}
