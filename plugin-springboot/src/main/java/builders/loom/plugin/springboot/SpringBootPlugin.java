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

package builders.loom.plugin.springboot;

import builders.loom.api.AbstractPlugin;

public class SpringBootPlugin extends AbstractPlugin<SpringBootPluginSettings> {

    public SpringBootPlugin() {
        super(new SpringBootPluginSettings());
    }

    @Override
    public void configure() {
        final SpringBootPluginSettings pluginSettings = getPluginSettings();

        if (pluginSettings.getVersion() == null) {
            throw new IllegalStateException("Missing required setting: springboot.version");
        }

        task("springBootApplication")
            .impl(() -> new SpringBootTask(getBuildConfig(), pluginSettings))
            .provides("springBootApplication")
            .uses("processedResources", "compilation", "compileDependencies")
            .register();

        goal("build")
            .requires("springBootApplication")
            .register();
    }

}