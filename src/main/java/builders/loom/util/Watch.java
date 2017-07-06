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

public final class Watch {

    private final long startTime;
    private long duration = -1;

    public Watch(final long startTime) {
        this.startTime = startTime;
    }

    public void stop(final long stopTime) {
        if (duration != -1) {
            throw new IllegalStateException("Watch already stopped");
        }
        duration = stopTime - startTime;
    }

    public long getDuration() {
        return duration;
    }

}