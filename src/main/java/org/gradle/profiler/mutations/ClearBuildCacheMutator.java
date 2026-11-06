package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.util.Arrays;

public class ClearBuildCacheMutator extends AbstractCacheCleanupMutator {

    private final File buildCacheDir;

    public ClearBuildCacheMutator(File gradleUserHome, Schedule schedule) {
        this(gradleUserHome, schedule, null);
    }

    public ClearBuildCacheMutator(File gradleUserHome, Schedule schedule, File buildCacheDir) {
        super(gradleUserHome, schedule, "build-cache-");
        this.buildCacheDir = buildCacheDir;
    }

    @Override
    protected void executeOnSchedule() {
        if (buildCacheDir == null) {
            super.executeOnSchedule();
        } else {
            System.out.println("> Cleaning build cache in " + buildCacheDir);
            if (!buildCacheDir.exists()) {
                return;
            }
            if (!buildCacheDir.isDirectory()) {
                throw new IllegalArgumentException(String.format("Cannot clear '%s' since it is not a directory", buildCacheDir.getAbsolutePath()));
            }
            cleanupCacheDir(buildCacheDir);
        }
    }

    @Override
    protected void cleanupCacheDir(File cacheDir) {
        File[] entries = cacheDir.listFiles(ClearBuildCacheMutator::shouldRemoveFile);
        if (entries == null) {
            throw new IllegalStateException("Cannot list build cache directory '" + cacheDir.getAbsolutePath() + "'");
        }
        Arrays.stream(entries).forEach(AbstractScheduledMutator::deleteFileOrDirectory);
    }

    private static boolean shouldRemoveFile(File file) {
        // First generation Build-cache contains hashes of length 32 as names, second generation uses a H2 database
        return file.getName().length() == 32 || file.getName().endsWith(".db");
    }

    public static class Configurator extends AbstractScheduledMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule) {
            String path = ConfigUtil.string(spec.getScenario(), "build-cache-dir", null);
            File buildCacheDir = path == null ? null : AbstractFileSystemMutator.resolveProjectFile(spec.getProjectDir(), path);
            return new ClearBuildCacheMutator(spec.getGradleUserHome(), schedule, buildCacheDir);
        }
    }
}
