package jobt.plugin.mavenresolver;

import java.util.ArrayList;
import java.util.List;

import jobt.api.AbstractTask;
import jobt.api.BuildConfig;
import jobt.api.Classpath;
import jobt.api.DependencyScope;
import jobt.api.TaskStatus;
import jobt.api.UsedProducts;

public class MavenResolverTask extends AbstractTask {

    private final DependencyScope dependencyScope;
    private final BuildConfig buildConfig;

    private final UsedProducts input;
    private final MavenResolver mavenResolver;

    public MavenResolverTask(final DependencyScope dependencyScope,
                             final BuildConfig buildConfig,
                             final MavenResolver mavenResolver,
                             final UsedProducts input) {

        this.dependencyScope = dependencyScope;
        this.buildConfig = buildConfig;
        this.mavenResolver = mavenResolver;
        this.input = input;
    }

    @Override
    public void prepare() throws Exception {
        mavenResolver.init();
    }

    @Override
    public TaskStatus run() throws Exception {
        switch (dependencyScope) {
            case COMPILE:
                compileScope();
                return TaskStatus.OK;
            case TEST:
                testScope();
                return TaskStatus.OK;
            default:
                throw new IllegalStateException("Unknown scope: " + dependencyScope);
        }
    }

    private void compileScope() throws Exception {
        final List<String> dependencies = new ArrayList<>(buildConfig.getDependencies());
        getProvidedProducts().complete("compileDependencies", new Classpath(mavenResolver.resolve(dependencies,
            DependencyScope.COMPILE)));
    }

    private void testScope() throws Exception {
        final List<String> dependencies = new ArrayList<>(buildConfig.getDependencies());
        dependencies.addAll(buildConfig.getTestDependencies());

        getProvidedProducts().complete("testDependencies", new Classpath(mavenResolver.resolve(dependencies,
            DependencyScope.TEST)));
    }

}
