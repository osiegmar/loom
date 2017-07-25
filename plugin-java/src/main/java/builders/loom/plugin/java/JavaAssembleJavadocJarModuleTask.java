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
import java.util.Optional;
import java.util.jar.JarOutputStream;

import builders.loom.api.AbstractModuleTask;
import builders.loom.api.TaskResult;
import builders.loom.api.product.AssemblyProduct;
import builders.loom.api.product.ResourcesTreeProduct;

public class JavaAssembleJavadocJarModuleTask extends AbstractModuleTask {

    @Override
    public TaskResult run() throws Exception {
        final Optional<ResourcesTreeProduct> resourcesTreeProduct = useProduct(
            "javadoc", ResourcesTreeProduct.class);

        if (!resourcesTreeProduct.isPresent()) {
            return completeSkip();
        }

        final Path buildDir = Files.createDirectories(Paths.get("loombuild", "libs"));

        final Path jarFile = buildDir.resolve(String.format("%s-javadoc.jar",
            getBuildContext().getModuleName()));

        try (final JarOutputStream os = new JarOutputStream(Files.newOutputStream(jarFile))) {
            FileUtil.copy(resourcesTreeProduct.get().getSrcDir(), os);
        }

        return completeOk(new AssemblyProduct(jarFile, "Jar of Javadoc"));
    }

}