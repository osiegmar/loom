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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import builders.loom.api.AbstractModuleTask;
import builders.loom.api.TaskResult;
import builders.loom.api.product.GenericProduct;
import builders.loom.api.product.OutputInfo;
import builders.loom.api.product.Product;
import builders.loom.util.ProductChecksumUtil;

public class JavaAssembleJavadocJarTask extends AbstractModuleTask {

    private static final Logger LOG = LoggerFactory.getLogger(JavaAssembleJavadocJarTask.class);

    @Override
    public TaskResult run() throws Exception {
        final Optional<Product> resourcesTreeProduct =
            useProduct("javadoc", Product.class);

        if (!resourcesTreeProduct.isPresent()) {
            return TaskResult.empty();
        }

        final Path jarFile = Files
            .createDirectories(resolveBuildDir("javadoc-jar"))
            .resolve(String.format("%s-javadoc.jar", getBuildContext().getModuleName()));

        final ToolProvider toolProvider = ToolProvider.findFirst("jar")
            .orElseThrow(() -> new IllegalStateException("Couldn't find jar ToolProvider"));

        final List<String> args = new ArrayList<>(List.of("-c", "-f", jarFile.toString()));

        resourcesTreeProduct.ifPresent(p ->
            args.addAll(List.of("-C", p.getProperty("javaDocOut"), ".")));

        LOG.debug("Run JarToolProvider with args: {}", args);
        final int result = toolProvider.run(System.out, System.err, args.toArray(new String[]{}));

        if (result != 0) {
            throw new IllegalStateException("Building sources-jar file failed");
        }

        return TaskResult.done(newProduct(jarFile));
    }

    private static Product newProduct(final Path jarFile) {
        return new GenericProduct("javaDocJarFile", jarFile.toString(),
            ProductChecksumUtil.recursiveMetaChecksum(jarFile),
            new OutputInfo("Jar of Javadoc", jarFile.toString()));
    }

}
