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

package builders.loom.core.plugin;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import builders.loom.api.BuildConfig;
import builders.loom.api.BuildConfigWithSettings;
import builders.loom.api.ModuleBuildConfig;
import builders.loom.api.Plugin;
import builders.loom.api.PluginSettings;
import builders.loom.core.misc.BeanUtil;
import builders.loom.core.misc.ExtensionLoader;
import builders.loom.util.SystemUtil;

public class PluginLoader {

    private static final Logger LOG = LoggerFactory.getLogger(PluginLoader.class);

    private final Path loomBaseDir = SystemUtil.determineLoomBaseDir();
    private final Map<String, Class<?>> pluginClasses = new HashMap<>();

    public void initPlugins(final Map<String, String> availablePlugins,
                            final Set<String> pluginsToInitialize,
                            final BuildConfig moduleConfig,
                            final TaskRegistryImpl taskRegistry) {

        final Set<String> acceptedSettings = new HashSet<>();
        for (final String plugin : pluginsToInitialize) {
            acceptedSettings.addAll(initPlugin(availablePlugins, plugin, moduleConfig,
                taskRegistry));
        }

        validateConfiguredTasks(taskRegistry);

        if (moduleConfig instanceof BuildConfigWithSettings) {
            validateSettings((BuildConfigWithSettings) moduleConfig, acceptedSettings);
        }
    }

    private Set<String> initPlugin(final Map<String, String> availablePlugins,
                                   final String pluginName, final BuildConfig config,
                                   final TaskRegistryImpl taskRegistry) {

        final Plugin plugin = getPlugin(availablePlugins, pluginName);

        plugin.setName(pluginName);
        plugin.setTaskRegistry(taskRegistry);
        if (config instanceof ModuleBuildConfig) {
            plugin.setModuleBuildConfig((ModuleBuildConfig) config);
        }

        final Set<String> acceptedSettings = injectPluginSettings(pluginName, plugin, config);

        plugin.configure();

        LOG.info("Plugin {} initialized", pluginName);

        return acceptedSettings;
    }

    private Plugin getPlugin(final Map<String, String> availablePlugins, final String pluginName) {
        final String pluginClassname = availablePlugins.get(pluginName);
        if (pluginClassname == null) {
            throw new IllegalArgumentException("Unknown plugin: " + pluginName);
        }

        final Class<?> pluginClass = pluginClasses.computeIfAbsent(pluginName, (t) ->
            ExtensionLoader.loadExtension(loomBaseDir, "plugin-" + pluginName, pluginClassname));

        try {
            return (Plugin) pluginClass.getDeclaredConstructor().newInstance();
        } catch (final InstantiationException | IllegalAccessException | NoSuchMethodException
            | InvocationTargetException e) {
            throw new IllegalStateException("Error initializing Plugin " + pluginName, e);
        }
    }

    @SuppressWarnings("checkstyle:illegalcatch")
    private static Set<String> injectPluginSettings(final String plugin, final Plugin regPlugin,
                                                    final BuildConfig moduleConfig) {

        final PluginSettings pluginSettings = regPlugin.getPluginSettings();

        if (pluginSettings == null) {
            return Collections.emptySet();
        }

        final Set<String> configuredPluginSettings = new HashSet<>();

        if (moduleConfig instanceof BuildConfigWithSettings) {
            final BuildConfigWithSettings moduleConfigWithSettings =
                (BuildConfigWithSettings) moduleConfig;
            final Map<String, String> settings = moduleConfigWithSettings.getSettings();
            final List<String> properties = settings.keySet().stream()
                .filter(k -> k.startsWith(plugin + "."))
                .collect(Collectors.toList());

            for (final String property : properties) {
                final String propertyName = property.substring(plugin.length() + 1);
                final String propertyValue = settings.get(property);

                try {
                    BeanUtil.set(pluginSettings, propertyName, propertyValue);
                } catch (final Exception e) {
                    throw new IllegalStateException("Error injecting settings into plugin "
                        + plugin, e);
                }

                configuredPluginSettings.add(property);
            }
        }

        return configuredPluginSettings;
    }

    private void validateConfiguredTasks(final TaskRegistryLookup taskRegistry) {
        final Set<String> providedProducts = taskRegistry.configuredTasks().stream()
            .map(ConfiguredTask::getProvidedProduct)
            .collect(Collectors.toSet());

        for (final ConfiguredTask configuredTask : taskRegistry.configuredTasks()) {
            final Set<String> usedProducts = configuredTask.getUsedProducts();
            for (final String usedProduct : usedProducts) {
                if (!providedProducts.contains(usedProduct)) {
                    throw new IllegalStateException(
                        String.format("Task %s requests non existing product <%s>",
                            configuredTask.getName(), usedProduct));
                }
            }
        }
    }

    private void validateSettings(final BuildConfigWithSettings moduleConfig,
                                  final Set<String> configuredPluginSettings) {
        final Set<String> unknownSettings = new HashSet<>(moduleConfig.getSettings().keySet());
        unknownSettings.removeAll(configuredPluginSettings);

        if (!unknownSettings.isEmpty()) {
            LOG.warn("Unknown settings: " + unknownSettings);
        }
    }

}
