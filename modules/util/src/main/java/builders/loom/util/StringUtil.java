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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class StringUtil {

    private StringUtil() {
    }

    public static List<String> split(final String input, final String regex) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(input.split(regex))
            .map(String::trim)
            .filter(str -> !str.isEmpty())
            .collect(Collectors.toList());
    }

    @SuppressWarnings("checkstyle:returncount")
    public static String substringAfter(final String str, final String separator) {
        if (str == null) {
            return null;
        }

        if (separator == null) {
            return "";
        }

        final int idx = str.indexOf(separator);
        if (idx == -1) {
            return "";
        }

        return str.substring(idx + separator.length());
    }

}
