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

package builders.loom.plugin.checkstyle;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.ModuleFactory;
import com.puppycrawl.tools.checkstyle.PackageObjectFactory;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.XMLLogger;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.RootModule;

import builders.loom.api.AbstractModuleTask;
import builders.loom.api.CompileTarget;
import builders.loom.api.LoomPaths;
import builders.loom.api.RepositoryPathAware;
import builders.loom.api.TaskResult;
import builders.loom.api.product.ManagedGenericProduct;
import builders.loom.api.product.OutputInfo;
import builders.loom.api.product.Product;
import builders.loom.util.ClassLoaderUtil;
import builders.loom.util.ProductChecksumUtil;

@SuppressWarnings({"checkstyle:classdataabstractioncoupling", "checkstyle:classfanoutcomplexity"})
public class CheckstyleTask extends AbstractModuleTask implements RepositoryPathAware {

    private static final Logger LOG = LoggerFactory.getLogger(CheckstyleTask.class);

    private final CompileTarget compileTarget;

    private final CheckstylePluginSettings pluginSettings;
    private final String sourceProductId;
    private final String reportOutputDescription;
    private Path repositoryPath;

    public CheckstyleTask(final CompileTarget compileTarget,
                          final CheckstylePluginSettings pluginSettings) {
        this.compileTarget = compileTarget;
        this.pluginSettings = pluginSettings;

        if (pluginSettings.getConfigLocation() == null) {
            throw new IllegalStateException("Missing configuration: checkstyle.configLocation");
        }

        switch (compileTarget) {
            case MAIN:
                sourceProductId = "source";
                reportOutputDescription = "Checkstyle main report";
                break;
            case TEST:
                sourceProductId = "testSource";
                reportOutputDescription = "Checkstyle test report";
                break;
            default:
                throw new IllegalStateException("Unknown compileTarget " + compileTarget);
        }
    }

    @Override
    public void setRepositoryPath(final Path repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    @Override
    public TaskResult run() throws Exception {

        final List<File> files = listSourceFiles();

        if (files.isEmpty()) {
            return TaskResult.empty();
        }

        LOG.info("Start analyzing {} source files with Checkstyle", files.size());

        final Path reportDir =
            Files.createDirectories(resolveReportDir("checkstyle", compileTarget));

        final RootModule rootModule = createRootModule();
        rootModule.addListener(new LoggingAuditListener());
        rootModule.addListener(newXmlLogger(reportDir.resolve("checkstyle-report.xml")));

        try {
            final int errors = rootModule.process(files);

            if (errors > 0) {
                return TaskResult.fail(newProduct(reportDir, reportOutputDescription),
                    "Checkstyle reported " + errors + " errors");
            }
        } finally {
            rootModule.destroy();
        }

        return TaskResult.done(newProduct(reportDir, reportOutputDescription));
    }

    private List<File> listSourceFiles() throws InterruptedException, IOException {
        final Optional<Product> sourceTree =
            useProduct(sourceProductId, Product.class);

        if (!sourceTree.isPresent()) {
            return Collections.emptyList();
        }

        final Path srcDir = Paths.get(sourceTree.get().getProperty("srcDir"));

        // Checkstyle doesn't support module-info.java, so skip it
        return Files
            .find(srcDir, Integer.MAX_VALUE, (path, attr) -> attr.isRegularFile())
            .filter(f -> !f.getFileName().toString().equals(LoomPaths.MODULE_INFO_JAVA))
            .map(Path::toFile)
            .collect(Collectors.toList());
    }

    private RootModule createRootModule() throws InterruptedException {
        final String configLocation = determineConfigLocation();
        LOG.debug("Read config from {}", configLocation);

        try {
            final Properties props = createOverridingProperties(configLocation);

            LOG.debug("Checkstyle properties: {}", props);

            final Configuration config =
                ConfigurationLoader.loadConfiguration(configLocation,
                    new PropertiesExpander(props));

            final ClassLoader moduleClassLoader = Checker.class.getClassLoader();

            final ModuleFactory factory = new PackageObjectFactory(
                Checker.class.getPackage().getName(), moduleClassLoader);

            final Object module = factory.createModule(config.getName());

            if (!(module instanceof Checker)) {
                throw new IllegalStateException("Only Checker root module is supported. "
                    + "Got " + module.getClass());
            }

            final Checker checker = (Checker) factory.createModule(config.getName());
            checker.setModuleClassLoader(moduleClassLoader);
            checker.setCharset("UTF-8");

            // Checker.setClassLoader is planned to be removed -
            // https://github.com/checkstyle/checkstyle/issues/3773
            // it is only required for JavadocMethodCheck
            buildClassLoader().ifPresent(checker::setClassLoader);

            checker.configure(config);

            if (getRuntimeConfiguration().isCacheEnabled()) {
                final Path cacheFile = repositoryPath
                    .resolve(getBuildContext().getModuleName())
                    .resolve(compileTarget.name().toLowerCase())
                    .resolve("checkstyle.cache");

                try {
                    checker.setCacheFile(cacheFile
                        .toAbsolutePath().normalize().toString());
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            return checker;
        } catch (final CheckstyleException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Unable to create Root Module with configuration: "
                + configLocation, e);
        }
    }

    private Optional<ClassLoader> buildClassLoader() throws InterruptedException {
        final List<URL> urls = new ArrayList<>();

        switch (compileTarget) {
            case MAIN:
                useProduct("compilation", Product.class)
                    .map(p -> Paths.get(p.getProperty("classesDir")))
                    .ifPresent(c -> urls.add(ClassLoaderUtil.toUrl(c)));

                useProduct("compileDependencies", Product.class)
                    .map(p -> p.getProperties("classpath"))
                    .ifPresent(p -> p.forEach(c -> urls.add(ClassLoaderUtil.toUrl(Paths.get(c)))));
                break;
            case TEST:
                useProduct("testCompilation", Product.class)
                    .map(p -> Paths.get(p.getProperty("classesDir")))
                    .ifPresent(c -> urls.add(ClassLoaderUtil.toUrl(c)));

                useProduct("compilation", Product.class)
                    .map(p -> Paths.get(p.getProperty("classesDir")))
                    .ifPresent(c -> urls.add(ClassLoaderUtil.toUrl(c)));

                useProduct("testDependencies", Product.class)
                    .map(p -> p.getProperties("classpath"))
                    .ifPresent(p -> p.forEach(c -> urls.add(ClassLoaderUtil.toUrl(Paths.get(c)))));
                break;
            default:
                throw new IllegalStateException("Unknown compileTarget " + compileTarget);
        }

        return urls.isEmpty()
            ? Optional.empty()
            : Optional.of(new URLClassLoader(urls.toArray(new URL[]{})));
    }

    private String determineConfigLocation() {
        final String configLocation = pluginSettings.getConfigLocation();
        if (isEmbeddedConfiguration(configLocation)) {
            return configLocation;
        }

        return getBuildContext().getPath().resolve(configLocation)
            .toAbsolutePath().normalize().toString();
    }

    private boolean isEmbeddedConfiguration(final String configLocation) {
        return configLocation.startsWith("/");
    }

    private Properties createOverridingProperties(final String configLocation) {
        final Properties properties = new Properties();

        // Set the same variables as the checkstyle plugin for eclipse
        // http://eclipse-cs.sourceforge.net/#!/properties
        final Path baseDir = getRuntimeConfiguration().getProjectBaseDir()
            .toAbsolutePath().normalize();
        properties.setProperty("basedir", baseDir.toString());
        properties.setProperty("project_loc", baseDir.toString());

        if (!isEmbeddedConfiguration(pluginSettings.getConfigLocation())) {
            final Path checkstyleConfigDir = Paths.get(configLocation).getParent();
            properties.setProperty("samedir", checkstyleConfigDir.toString());
            properties.setProperty("config_loc", checkstyleConfigDir.toString());
        }

        return properties;
    }

    private XMLLogger newXmlLogger(final Path reportFile) throws IOException {
        return new XMLLogger(new BufferedOutputStream(Files.newOutputStream(reportFile)),
            AutomaticBean.OutputStreamOptions.CLOSE);
    }

    private static Product newProduct(final Path reportDir, final String outputInfo) {
        // note: skiphints can be based on report file meta data,
        //   because checkstyle uses a cache to prevent recreation of identical reports
        return new ManagedGenericProduct("reportDir", reportDir.toString(),
            ProductChecksumUtil.recursiveMetaChecksum(reportDir),
            new OutputInfo(outputInfo, reportDir));
    }
}
