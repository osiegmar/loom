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

package builders.loom.plugin.eclipse;

import builders.loom.api.AbstractPlugin;
import builders.loom.api.PluginSettings;

public class EclipsePlugin extends AbstractPlugin<PluginSettings> {

    @Override
    public void configure() {
        task("createEclipseFiles")
            .impl(() -> new EclipseTask())
            .provides("eclipse")
            .importFromAllModules("compileArtifacts", "testArtifacts")
            .desc("Generates/updates .classpath/.project/.settings for Eclipse IDE.")
            .register();

        task("cleanEclipseFiles")
            .impl(() -> new EclipseCleanTask())
            .provides("cleanEclipse")
            .desc("Cleans all Eclipse IDE project files.")
            .register();
    }

}
