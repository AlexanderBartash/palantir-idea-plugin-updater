/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.ideapluginupdater;

import com.intellij.DynamicBundle;
import com.intellij.openapi.application.ApplicationManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Allows getting localized messages from {@code resources/i18n/HybrisBundle.properties}.
 *
 * @author AlexanderBartash@gmail.com
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/internationalization.html">Internationalization</a>
 */
public class PalantirI18nBundle {

    /** See {@code resources/i18n/HybrisBundle.properties}. */
    private static final String PATH_TO_BUNDLE = "i18n.PalantirBundle";

    @Contract(pure = true)
    public static PalantirI18nBundle getInstance() {
        return ApplicationManager.getApplication().getService(PalantirI18nBundle.class);
    }

    private final DynamicBundle dynamicBundle = new DynamicBundle(PalantirI18nBundle.class, PATH_TO_BUNDLE);

    /**
     * Returns a localized message from {@code resources/i18n/HybrisBundle.properties} for the given key.
     *
     * @param key The key to get the localization for.
     * @return The localized String
     */
    @NotNull
    @Contract(pure = true)
    public String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) final String key) {
        return this.dynamicBundle.getMessage(key);
    }

    /**
     * Returns a localized message from {@code resources/i18n/HybrisBundle.properties} for the given key.
     *
     * @param key The key to get the localization for.
     * @param params The parameters used to format the returned string.
     * @return The localized String
     */
    @NotNull
    @Contract(pure = true)
    public String message(
            @NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) final String key, @NotNull final Object... params) {
        final String message = this.dynamicBundle.getMessage(key, params);
        return StringUtils.isBlank(message) ? key : message;
    }
}
