package org.gradle.profiler.mutations

import static org.gradle.profiler.mutations.AbstractScheduledMutator.Schedule.BUILD
import static org.gradle.profiler.mutations.AbstractScheduledMutator.Schedule.CLEANUP
import static org.gradle.profiler.mutations.AbstractScheduledMutator.Schedule.SCENARIO

class ClearBuildCacheMutatorTest extends AbstractMutatorTest {

    File gradleUserHome
    File buildCache1Entry
    File buildCache2Entry

    def setup() {
        gradleUserHome = tmpDir.newFolder()
        buildCache1Entry = new File(gradleUserHome, "caches/build-cache-1/12345678901234567890123456789012")
        buildCache2Entry = new File(gradleUserHome, "caches/build-cache-2/filestore.mv.db")
    }

    private void remakeCacheEntries() {
        buildCache1Entry.parentFile.mkdirs()
        buildCache1Entry.createNewFile()
        buildCache2Entry.parentFile.mkdirs()
        buildCache2Entry.createNewFile()
    }

    def "clears only configured build cache entries on #schedule schedule"() {
        given:
        def cacheDir = tmpDir.newFolder()
        def entries = ["12345678901234567890123456789012", "filestore.mv.db"].collect { new File(cacheDir, it) }
        def retained = ["build-cache.lock", "gc.properties", "unrelated.txt"].collect { new File(cacheDir, it) }
        retained.each { it.text = "keep" }
        remakeCacheEntries()
        def mutator = new ClearBuildCacheMutator(gradleUserHome, schedule, cacheDir)

        expect:
        [
            { mutator.beforeScenario(scenarioContext) },
            { mutator.beforeCleanup(buildContext) },
            { mutator.afterCleanup(buildContext, null) },
            { mutator.beforeBuild(buildContext) },
            { mutator.afterBuild(buildContext, null) },
            { mutator.afterScenario(scenarioContext) }
        ].withIndex().every { callback, index ->
            entries.each { it.text = "cached" }
            callback()
            entries.every { it.exists() == (index != scheduledCallback) }
        }
        retained.every { it.text == "keep" }
        cacheDir.isDirectory()
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        where:
        schedule | scheduledCallback
        SCENARIO | 0
        CLEANUP  | 1
        BUILD    | 3
    }

    def "missing configured cache is empty"() {
        def cacheDir = new File(tmpDir.root, "missing")
        def mutator = new ClearBuildCacheMutator(gradleUserHome, schedule, cacheDir)

        when:
        mutator.beforeScenario(scenarioContext)
        mutator.beforeBuild(buildContext)

        then:
        noExceptionThrown()
        !cacheDir.exists()

        where:
        schedule << [SCENARIO, BUILD]
    }

    def "reports configured cache path when it is a regular file"() {
        def cacheFile = tmpDir.newFile()
        def mutator = new ClearBuildCacheMutator(gradleUserHome, SCENARIO, cacheFile)

        when:
        mutator.beforeScenario(scenarioContext)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message.contains(cacheFile.absolutePath)
    }

    def "default cleanup preserves cache roots and unrelated files"() {
        given:
        remakeCacheEntries()
        def retained = [buildCache1Entry, buildCache2Entry].collectMany { entry ->
            ["build-cache.lock", "gc.properties", "unrelated.txt"].collect { new File(entry.parentFile, it) }
        }
        retained.each { it.text = "keep" }

        when:
        new ClearBuildCacheMutator(gradleUserHome, SCENARIO).beforeScenario(scenarioContext)

        then:
        !buildCache1Entry.exists()
        !buildCache2Entry.exists()
        buildCache1Entry.parentFile.isDirectory()
        buildCache2Entry.parentFile.isDirectory()
        retained.every { it.text == "keep" }
    }

    def "reports cache path when directory listing fails"() {
        def cacheDir = Stub(File) {
            exists() >> true
            isDirectory() >> true
            getAbsolutePath() >> "/unreadable/build-cache"
            listFiles(_ as FileFilter) >> null
        }

        when:
        new ClearBuildCacheMutator(gradleUserHome, SCENARIO, cacheDir).beforeScenario(scenarioContext)

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains(cacheDir.absolutePath)
    }

    def "clears build caches before scenario starts"() {
        def mutator = new ClearBuildCacheMutator(gradleUserHome, SCENARIO)

        when:
        remakeCacheEntries()
        mutator.beforeScenario(scenarioContext)
        then:
        !buildCache1Entry.exists()
        !buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeCleanup(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterCleanup(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeBuild(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterBuild(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()
    }

    def "clears build caches before cleanup starts"() {
        def mutator = new ClearBuildCacheMutator(gradleUserHome, CLEANUP)

        when:
        remakeCacheEntries()
        mutator.beforeScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeCleanup(buildContext)
        then:
        !buildCache1Entry.exists()
        !buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterCleanup(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeBuild(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterBuild(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()
    }

    def "clears build caches before build starts"() {
        def mutator = new ClearBuildCacheMutator(gradleUserHome, BUILD)

        when:
        remakeCacheEntries()
        mutator.beforeScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeCleanup(buildContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterCleanup(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.beforeBuild(buildContext)
        then:
        !buildCache1Entry.exists()
        !buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterBuild(buildContext, null)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()

        when:
        remakeCacheEntries()
        mutator.afterScenario(scenarioContext)
        then:
        buildCache1Entry.exists()
        buildCache2Entry.exists()
    }
}
