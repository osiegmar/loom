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

package builders.loom.util;

import java.util.Objects;
import java.util.Properties;

public class PropertiesMerger {

    private final Properties wrapper;
    private boolean changed;

    public PropertiesMerger(final Properties properties) {
        Objects.requireNonNull(properties);
        this.wrapper = properties;
    }

    /**
     * Set value.
     */
    public void set(final String key, final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (!wrapper.containsKey(key)) {
            wrapper.setProperty(key, value);
            changed = true;
        } else {
            final String oldValue = wrapper.getProperty(key);
            if (!oldValue.equals(value)) {
                wrapper.setProperty(key, value);
                changed = true;
            }
        }
    }

    /**
     * Replace value with new value only if it exists.
     */
    public void fixup(final String key, final String newValue) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(newValue);
        if (wrapper.containsKey(key)) {
            final Object prevValue = wrapper.setProperty(key, newValue);
            changed |= !newValue.equals(prevValue);
        }
    }

    /**
     * Set value if not defined.
     */
    public void setIfAbsent(final String key, final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (!wrapper.containsKey(key)) {
            wrapper.setProperty(key, value);
            changed = true;
        }
    }

    /**
     * Report, if values have changed through merger wrapper.
     * Note: direct changes made to the wrapped object are not tracked!
     */
    public boolean isChanged() {
        return changed;
    }
}
