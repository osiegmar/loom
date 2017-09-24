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

package builders.loom.plugin.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import builders.loom.api.AbstractModuleTask;
import builders.loom.api.DependencyResolverService;
import builders.loom.api.DependencyScope;
import builders.loom.api.TaskResult;
import builders.loom.api.product.ArtifactProduct;
import builders.loom.api.product.GenericProduct;
import builders.loom.api.product.Product;
import builders.loom.util.ProductChecksumUtil;

public class ArtifactResolverTask extends AbstractModuleTask {

    private final DependencyScope dependencyScope;
    private final DependencyResolverService dependencyResolver;

    public ArtifactResolverTask(final DependencyScope dependencyScope,
                                final DependencyResolverService dependencyResolver) {

        this.dependencyScope = dependencyScope;
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public TaskResult run(final boolean skip) throws Exception {
        final List<String> dependencies = listDependencies();

        if (dependencies.isEmpty()) {
            return TaskResult.empty();
        }

        final List<ArtifactProduct> artifacts = resolve(dependencies, true);
        return TaskResult.ok(newProduct(artifacts));
    }

    List<String> listDependencies() {
        final List<String> deps = new ArrayList<>(getModuleConfig().getCompileDependencies());

        switch (dependencyScope) {
            case COMPILE:
                break;
            case TEST:
                deps.addAll(getModuleConfig().getTestDependencies());
                break;
            default:
                throw new IllegalStateException("Unknown scope: " + dependencyScope);
        }

        return deps;
    }

    protected List<ArtifactProduct> resolve(final List<String> dependencies,
                                            final boolean withSources) {
        return dependencyResolver
            .resolveArtifacts(dependencies, dependencyScope, withSources).stream()
            .map(a -> new ArtifactProduct(a.getMainArtifact(), a.getSourceArtifact()))
            .collect(Collectors.toList());
    }

    private static Product newProduct(final List<ArtifactProduct> artifacts) {
        // FIXME evil hack
        final Map<String, List<String>> properties = Map.of(
            "artifacts",
            artifacts.stream()
                .map(a -> a.getMainArtifact() + "#" + a.getSourceArtifact())
                .collect(Collectors.toList())
        );
        return new GenericProduct(properties, ProductChecksumUtil.calcChecksum(properties), null);
    }

}
