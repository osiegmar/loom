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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarOutputStream;

import builders.loom.api.AbstractModuleTask;
import builders.loom.api.TaskResult;
import builders.loom.api.product.GenericProduct;
import builders.loom.api.product.Product;
import builders.loom.util.ProductChecksumUtil;

public class JavaAssembleSourcesJarTask extends AbstractModuleTask {

    @Override
    public TaskResult run(final boolean skip) throws Exception {
        final Optional<Product> sourceTree = useProduct(
            "source", Product.class);

        final Optional<Product> resourcesTreeProduct = useProduct(
            "resources", Product.class);

        if (!sourceTree.isPresent() && !resourcesTreeProduct.isPresent()) {
            return TaskResult.empty();
        }

        final Path sourceJarFile = Files
            .createDirectories(resolveBuildDir("sources-jar"))
            .resolve(String.format("%s-sources.jar", getBuildContext().getModuleName()));

        try (JarOutputStream os = new JarOutputStream(Files.newOutputStream(sourceJarFile))) {
            if (sourceTree.isPresent()) {
                JavaFileUtil.copy(Paths.get(sourceTree.get().getProperty("srcDir")), os);
            }

            if (resourcesTreeProduct.isPresent()) {
                JavaFileUtil.copy(Paths.get(resourcesTreeProduct.get().getProperty("srcDir")), os);
            }
        }

        return TaskResult.ok(newProduct(sourceJarFile));
    }

    private static Product newProduct(final Path jarFile) {
        final Map<String, String> properties = Map.of("sourceJarFile", jarFile.toString());
        return new GenericProduct(properties, ProductChecksumUtil.calcChecksum(jarFile),
            "Jar of sources");
    }

}
