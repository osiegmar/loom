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

package builders.loom.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import builders.loom.api.BuildContext;
import builders.loom.api.LoomPaths;
import builders.loom.api.Module;
import builders.loom.api.ModuleBuildConfigAware;
import builders.loom.api.ModuleGraphAware;
import builders.loom.api.ProductDependenciesAware;
import builders.loom.api.ProductPromise;
import builders.loom.api.ProductRepository;
import builders.loom.api.RuntimeConfiguration;
import builders.loom.api.Task;
import builders.loom.api.TaskResult;
import builders.loom.api.TaskStatus;
import builders.loom.api.TestProgressEmitter;
import builders.loom.api.TestProgressEmitterAware;
import builders.loom.api.UsedProducts;
import builders.loom.api.product.GenericProduct;
import builders.loom.api.product.Product;
import builders.loom.core.plugin.ConfiguredTask;
import builders.loom.util.Hasher;
import builders.loom.util.Iterables;
import builders.loom.util.serialize.Record;
import builders.loom.util.serialize.SimpleSerializer;

public class Job implements Callable<TaskStatus> {

    private static final Logger LOG = LoggerFactory.getLogger(Job.class);

    private final String name;
    private final BuildContext buildContext;
    private final RuntimeConfiguration runtimeConfiguration;
    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.INITIALIZING);
    private final ConfiguredTask configuredTask;
    private final ProductRepository productRepository;
    private final Map<Module, Set<Module>> transitiveModuleCompileDependencies;
    private UsedProducts usedProducts;
    private final Map<BuildContext, ProductRepository> moduleProductRepositories;
    private final Set<Module> modules;
    private final TestProgressEmitter testProgressEmitter;

    @SuppressWarnings("checkstyle:parameternumber")
    Job(final String name,
        final BuildContext buildContext,
        final RuntimeConfiguration runtimeConfiguration,
        final ConfiguredTask configuredTask,
        final ProductRepository productRepository,
        final Map<Module, Set<Module>> transitiveModuleCompileDependencies,
        final Map<BuildContext, ProductRepository> moduleProductRepositories,
        final TestProgressEmitter emitter) {

        this.name = Objects.requireNonNull(name, "name required");
        this.buildContext = buildContext;
        this.runtimeConfiguration = runtimeConfiguration;
        this.configuredTask = Objects.requireNonNull(configuredTask, "configuredTask required");
        this.productRepository =
            Objects.requireNonNull(productRepository, "productRepository required");
        this.transitiveModuleCompileDependencies = transitiveModuleCompileDependencies;
        this.moduleProductRepositories = moduleProductRepositories;
        this.modules = moduleProductRepositories.keySet().stream()
            .filter(Module.class::isInstance)
            .map(Module.class::cast).collect(Collectors.toSet());
        testProgressEmitter = emitter;
    }

    public String getName() {
        return name;
    }

    @Override
    public TaskStatus call() throws Exception {
        status.set(JobStatus.RUNNING);
        try {
            return runTask();
        } finally {
            status.set(JobStatus.STOPPED);
        }
    }

    // TODO add more logging
    public TaskStatus runTask() throws Exception {
        LOG.info("Start task {}", name);

        final ProductPromise productPromise = prepareProductPromise();

        final String signature = calcSignature();

        final Path productFile = buildFileName(".product");

        if (canSkipTask(signature) && Files.exists(productFile)) {
            final Path checksumFile = buildFileName(".product.checksum");

            final Map<String, List<String>> properties = new HashMap<>();

            SimpleSerializer.read(productFile, (e) -> {
                final String key = e.getFields().get(0);
                final List<String> values = e.getFields().subList(1, e.getFields().size());
                properties.put(key, values);
            });

            // TODO add outputInfo
            final GenericProduct genericProduct = new GenericProduct(properties,
                Files.readAllLines(checksumFile).get(0), null);
            final TaskResult taskResult = TaskResult.up2date(genericProduct);

            LOG.info("Task (skipped) resulted with {}", taskResult);

            // FIXME check result

            // note on fail status: product may contain details about the failure (reports)
            productPromise.complete(taskResult);

            return taskResult.getStatus();
        }

        clearProductChecksums();

        final Supplier<Task> taskSupplier = configuredTask.getTaskSupplier();
        Thread.currentThread().setContextClassLoader(taskSupplier.getClass().getClassLoader());
        final Task task = taskSupplier.get();
        injectTaskProperties(task);

        final TaskResult taskResult = task.run();

        LOG.info("Task resulted with {}", taskResult);

        if (taskResult == null) {
            throw new IllegalStateException("Task <" + name + "> must not return null");
        }
        if (taskResult.getStatus() == null) {
            throw new IllegalStateException("Task <" + name + "> must not return null status");
        }
        if (taskResult.getProduct() == null && taskResult.getStatus() != TaskStatus.EMPTY) {
            throw new IllegalStateException("Task <" + name + "> returned null product with "
                + "status: " + taskResult.getStatus());
        }

        saveProduct(taskResult);
        commitSignature(signature);

        // note on fail status: product may contain details about the failure (reports)
        productPromise.complete(taskResult);

        if (taskResult.getStatus() == TaskStatus.FAIL) {
            throw new IllegalStateException("Task <" + name + "> resulted in failure: "
                + taskResult.getErrorReason());
        }

        return taskResult.getStatus();
    }

    private ProductPromise prepareProductPromise() {
        final ProductPromise productPromise = productRepository
            .lookup(configuredTask.getProvidedProduct());

        productPromise.setStartTime(System.nanoTime());

        return productPromise;
    }

    private String calcSignature() throws InterruptedException {
        final UsedProducts productView = buildProductView();

        final List<String> skipHints = configuredTask.getSkipHints();

        if (skipHints.isEmpty()) {
            LOG.debug("No skip hints configured -- don't skip");
            return "PREVENT-SKIP:" + UUID.randomUUID().toString();
        }

        final List<String> checksumParts = new ArrayList<>(skipHints);

        // guarantee stable order
        final List<ProductPromise> orderedProductPromises = productView.getAllProducts().stream()
            .sorted(Comparator
                .comparing(ProductPromise::getModuleName)
                .thenComparing(ProductPromise::getProductId))
            .collect(Collectors.toList());

        for (final ProductPromise productPromise : orderedProductPromises) {
            final String productId = productPromise.getProductId();
            final String moduleName = productPromise.getModuleName();

            final String checksum = productView
                .readProduct(moduleName, productId, Product.class)
                .map(Product::checksum)
                .orElse("EMPTY");

            checksumParts.add(String.format("%s#%s:%s", moduleName, productId, checksum));
        }

        final String hash = Hasher.hash(checksumParts);

        LOG.debug("Signature: {}; Hash: {}", checksumParts, hash);

        return hash;
    }

    private UsedProducts buildProductView() {
        final Set<ProductPromise> productPromises = new HashSet<>();

        // inner module dependencies
        configuredTask.getUsedProducts().stream()
            .map(moduleProductRepositories.get(buildContext)::lookup)
            .forEach(productPromises::add);

        // explicit import from other modules
        final Set<String> importedProducts = configuredTask.getImportedProducts();
        if (!importedProducts.isEmpty()) {
            final Module module = (Module) this.buildContext;
            module.getConfig().getModuleCompileDependencies().stream()
                .flatMap(moduleName -> importedProducts.stream()
                    .map(p -> buildModuleProduct(moduleName, p)))
                .forEach(productPromises::add);
        }

        // import from all modules (e.g. for Eclipse / IntelliJ plugin)
        final Set<String> importedAllProducts = configuredTask.getImportedAllProducts();
        if (!importedAllProducts.isEmpty()) {
            modules.stream()
                .map(Module::getModuleName)
                .flatMap(moduleName -> importedAllProducts.stream()
                    .map(p -> buildModuleProduct(moduleName, p)))
                .forEach(productPromises::add);
        }

        return new UsedProducts(buildContext.getModuleName(), productPromises);
    }

    private void commitSignature(final String signature) {
        final Path checksumFile = buildFileName(".sig"); // TODO better suffix

        try {
            Files.write(checksumFile, List.of(signature), StandardOpenOption.CREATE_NEW); // TODO improve
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

	private boolean canSkipTask(final String signature) {
        final Path checksumFile = buildFileName(".sig"); // TODO better suffix
        if (Files.notExists(checksumFile)) {
            return false;
        }

        try {
            final String signatureLastRun = Iterables.getOnlyElement(Files.readAllLines(checksumFile)); // TODO improve

            final boolean equals = signatureLastRun.equals(signature);
            LOG.info("Last run: {} - current: {}; equals: {}", signatureLastRun, signature, equals);
            return equals;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private void clearProductChecksums() {
        try {
            Files.deleteIfExists(buildFileName(".product.checksum"));
            Files.deleteIfExists(buildFileName(".sig"));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void saveProduct(final TaskResult taskResult) {
        final Path checksumFile = buildFileName(".product.checksum");
        final Path productFile = buildFileName(".product");

        try {
            final String checksum;
            if (taskResult.getStatus() == TaskStatus.EMPTY) {
                checksum = "foo";
            } else {
                final Product product = taskResult.getProduct();
                final Map<String, List<String>> properties = product.getProperties();

                SimpleSerializer.write(productFile, properties.entrySet(), (e) -> {
                    final List<String> elements = new ArrayList<>();
                    elements.add(e.getKey());
                    elements.addAll(e.getValue());
                    return new Record(elements);
                });

                checksum = product.checksum();
            }

            Files.createDirectories(checksumFile.getParent());
            Files.write(checksumFile, List.of(checksum), StandardOpenOption.CREATE_NEW);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

	private Path buildFileName(final String suffix) {
        return LoomPaths.loomDir(runtimeConfiguration.getProjectBaseDir())
            .resolve(Paths.get(Version.getVersion(), "checksums",
                configuredTask.getBuildContext().getModuleName(),
                configuredTask.getProvidedProduct() + suffix));
    }

	private void injectTaskProperties(final Task task) {
        task.setRuntimeConfiguration(runtimeConfiguration);
        task.setBuildContext(buildContext);
        if (task instanceof ProductDependenciesAware) {
            final ProductDependenciesAware pdaTask = (ProductDependenciesAware) task;
            usedProducts = buildProductView();
            pdaTask.setUsedProducts(usedProducts);
        }
        if (task instanceof ModuleBuildConfigAware) {
            final ModuleBuildConfigAware mbcaTask = (ModuleBuildConfigAware) task;
            final Module module = (Module) buildContext;
            mbcaTask.setModuleBuildConfig(module.getConfig());
        }
        if (task instanceof ModuleGraphAware) {
            final ModuleGraphAware mgaTask = (ModuleGraphAware) task;
            mgaTask.setTransitiveModuleGraph(transitiveModuleCompileDependencies);
        }
        if (task instanceof TestProgressEmitterAware) {
            final TestProgressEmitterAware tpea = (TestProgressEmitterAware) task;
            tpea.setTestProgressEmitter(testProgressEmitter);
        }
    }

    private ProductPromise buildModuleProduct(final String moduleName, final String productId) {
        Objects.requireNonNull(moduleName, "moduleName required");
        Objects.requireNonNull(productId, "productId required");

        return moduleProductRepositories.entrySet().stream()
            .filter(e -> e.getKey().getModuleName().equals(moduleName))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Module <" + moduleName + "> not found"))
            .lookup(productId);
    }

    Optional<Set<ProductPromise>> getActuallyUsedProducts() {
        return Optional.ofNullable(usedProducts)
            .map(UsedProducts::getActuallyUsedProducts);
    }

    @Override
    public String toString() {
        return "Job{"
            + "name='" + name + '\''
            + ", status=" + status
            + '}';
    }

}
