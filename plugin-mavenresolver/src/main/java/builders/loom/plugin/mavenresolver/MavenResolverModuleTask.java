/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package builders.loom.plugin.mavenresolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import builders.loom.api.AbstractModuleTask;
import builders.loom.api.DependencyScope;
import builders.loom.api.TaskResult;
import builders.loom.api.product.ArtifactProduct;
import builders.loom.api.product.ClasspathProduct;

public class MavenResolverModuleTask extends AbstractModuleTask {

    private final DependencyScope dependencyScope;
    private final MavenResolverPluginSettings pluginSettings;
    private final Path cacheDir;
    private MavenResolver mavenResolver;

    public MavenResolverModuleTask(final DependencyScope dependencyScope,
                                   final MavenResolverPluginSettings pluginSettings,
                                   final Path cacheDir) {

        this.dependencyScope = dependencyScope;
        this.pluginSettings = pluginSettings;
        this.cacheDir = cacheDir;
    }

    @Override
    public TaskResult run() throws Exception {
        this.mavenResolver = MavenResolverSingleton.getInstance(pluginSettings, cacheDir);
        return completeOk(product());
    }

    private ClasspathProduct product() {
        switch (dependencyScope) {
            case COMPILE:
                return compileScope();
            case TEST:
                return testScope();
            default:
                throw new IllegalStateException("Unknown scope: " + dependencyScope);
        }
    }

    private ClasspathProduct compileScope() {
        final List<String> deps = new ArrayList<>(getModuleConfig().getCompileDependencies());
        final List<ArtifactProduct> artifactProducts = mavenResolver.resolve(deps,
            DependencyScope.COMPILE, null);

        final List<Path> collect = artifactProducts.stream()
            .map(ArtifactProduct::getMainArtifact).collect(Collectors.toList());

        return new ClasspathProduct(collect);
    }

    private ClasspathProduct testScope() {
        final List<String> deps = new ArrayList<>(getModuleConfig().getCompileDependencies());
        deps.addAll(getModuleConfig().getTestDependencies());

        final List<ArtifactProduct> artifactProducts = mavenResolver.resolve(deps,
            DependencyScope.TEST, null);

        final List<Path> collect = artifactProducts.stream()
            .map(ArtifactProduct::getMainArtifact).collect(Collectors.toList());

        return new ClasspathProduct(collect);
    }

}
