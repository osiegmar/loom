package jobt.plugin.java;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.resolution.DependencyResolutionException;

import jobt.FileCacher;
import jobt.MavenResolver;
import jobt.Progress;
import jobt.config.BuildConfig;
import jobt.plugin.Task;
import jobt.plugin.TaskStatus;

public class JavaCompileTask implements Task {

    public static final Path SRC_MAIN_PATH = Paths.get("src/main/java");
    public static final Path SRC_TEST_PATH = Paths.get("src/test/java");
    public static final Path BUILD_MAIN_PATH = Paths.get("jobtbuild", "classes", "main");
    public static final Path BUILD_TEST_PATH = Paths.get("jobtbuild", "classes", "test");

    private final BuildConfig buildConfig;
    private final MavenResolver mavenResolver;
    private final Path srcPath;
    private final Path buildDir;
    private final String dependencyScope;
    private final List<String> dependencies;
    private final List<Path> classpathAppendix = new ArrayList<>();
    private List<File> classPath;

    public JavaCompileTask(final BuildConfig buildConfig, final CompileTarget compileTarget,
                           final MavenResolver mavenResolver) {
        this.buildConfig = buildConfig;
        this.mavenResolver = mavenResolver;

        dependencies = new ArrayList<>();
        if (buildConfig.getDependencies() != null) {
            dependencies.addAll(buildConfig.getDependencies());
        }

        switch (compileTarget) {
            case MAIN:
                srcPath = SRC_MAIN_PATH;
                buildDir = BUILD_MAIN_PATH;
                dependencyScope = "compile";
                classpathAppendix.add(srcPath);
                break;
            case TEST:
                srcPath = SRC_TEST_PATH;
                buildDir = BUILD_TEST_PATH;
                dependencyScope = "test";
                classpathAppendix.add(srcPath);
                classpathAppendix.add(BUILD_MAIN_PATH);

                if (buildConfig.getTestDependencies() != null) {
                    dependencies.addAll(buildConfig.getTestDependencies());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown compileTarget " + compileTarget);
        }
    }

    @Override
    public TaskStatus run() throws Exception {
        final List<File> classpath = new ArrayList<>(getClassPath());
        classpath.addAll(classpathAppendix.stream().map(Path::toFile).collect(Collectors.toList()));

        final FileCacher fileCacher = new FileCacher();

        if (Files.notExists(buildDir)) {
            Files.createDirectories(buildDir);
        }

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector<JavaFileObject> diagnosticListener = new DiagnosticCollector<>();

        final List<String> options = buildOptions();

        final List<Path> srcFiles = Files.walk(srcPath)
            .filter(f -> Files.isRegularFile(f))
            .filter(f -> f.toString().endsWith(".java"))
            .collect(Collectors.toList());

        final List<File> nonCachedFiles;
        if (fileCacher.isCacheEmpty()) {
            nonCachedFiles = srcFiles.stream()
                .map(Path::toFile)
                .collect(Collectors.toList());
        } else {
            nonCachedFiles = srcFiles.stream()
                .filter(fileCacher::notCached)
                .map(Path::toFile)
                .collect(Collectors.toList());
        }

        if (nonCachedFiles.isEmpty()) {
            return TaskStatus.UP_TO_DATE;
        }

        final StandardJavaFileManager fileManager =
            compiler.getStandardFileManager(diagnosticListener, null, StandardCharsets.UTF_8);

        fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
            Collections.singletonList(buildDir.toFile()));

        final Iterable<? extends JavaFileObject> compUnits =
            fileManager.getJavaFileObjectsFromFiles(nonCachedFiles);

        if (!compiler.getTask(null, fileManager, diagnosticListener,
            options, null, compUnits).call()) {

            for (final Diagnostic<? extends JavaFileObject> diagnostic
                : diagnosticListener.getDiagnostics()) {
                Progress.log(diagnostic.toString());
            }

            throw new IllegalStateException("Compile failed");
        }

        fileCacher.cacheFiles(srcFiles);

        return TaskStatus.OK;
    }

    private List<String> buildOptions() {
        final List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(buildDir.toString());

        final Map<String, String> config = buildConfig.getConfiguration();
        final String sourceCompatibility = config.get("sourceCompatibility");
        if (sourceCompatibility != null) {
            options.add("-source");
            options.add(sourceCompatibility);
        }

        final String targetCompatibility = config.get("targetCompatibility");
        if (targetCompatibility != null) {
            options.add("-target");
            options.add(targetCompatibility);
        }

        options.add("-bootclasspath");
        options.add(buildRuntimePath());

        options.add("-Xlint:all");

        return options;
    }

    private String buildRuntimePath() {
        return Paths.get(System.getProperty("java.home"), "jre/lib/rt.jar").toString();
    }

    public List<File> getClassPath()
        throws DependencyCollectionException, DependencyResolutionException, IOException {

        if (classPath == null) {
            classPath = Collections.unmodifiableList(buildClasspath());
        }

        return classPath;
    }

    private List<File> buildClasspath() throws DependencyCollectionException,
        DependencyResolutionException, IOException {

        if (dependencies == null || dependencies.isEmpty()) {
            return classPath;
        }

        return mavenResolver.buildClasspath(dependencies, dependencyScope);
    }

}
