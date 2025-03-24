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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.Alarm;
import com.intellij.util.Alarm.ThreadToUse;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for {@link Project} open events in the IDE and for each project starts a background {@link Alarm}
 * on {@link ThreadToUse#POOLED_THREAD}, which regularly calls {@link PluginUpdateService#updatePluginsIfNeeded()}.
 *
 * @author AlexanderBartash@gmail.com
 */
public class PluginUpdateProjectActivity implements ProjectActivity, Disposable {
    /** The delay after the {@link Project} opening to the plugin update check. */
    private static final int INITIAL_CHECK_INTERVAL_MS = 30_000;
    /** The delay between the later update checks. */
    private static final int CHECK_INTERVAL_MS = 3_600_000;

    /**
     * Runs an activity after project open.
     * [execute] gets called inside a coroutine scope spanning from project opening to project closing
     * (or plugin unloading).
     * <p>
     * Flow and any other long-running activities are allowed and natural.
     *
     * @see com.intellij.openapi.startup.StartupManager
     * @see com.intellij.ide.util.RunOnceUtil
     */
    @Override
    public final Object execute(
            final @NotNull Project project, final @NotNull Continuation<? super Unit> continuation) {
        if (project.isDisposed()) {
            return Unit.INSTANCE;
        }

        // In the newer IDE versions we could check UpdateSettings.getInstance().isPluginsAutoUpdateEnabled()
        // and if it is true, do not run the plugin update, because the IDE does it for us automatically.
        final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        this.scheduleUpdateCheck(project, alarm, INITIAL_CHECK_INTERVAL_MS);
        return Unit.INSTANCE;
    }

    private void scheduleUpdateCheck(final @NotNull Project project, final @NotNull Alarm alarm, final long delayMs) {
        alarm.addRequest(
                () -> {
                    if (project.isDisposed()) {
                        return;
                    }

                    final PluginUpdateService pluginUpdateService = PluginUpdateService.getInstance(project);
                    pluginUpdateService.updatePluginsIfNeeded();

                    if (!project.isDisposed()) {
                        this.scheduleUpdateCheck(project, alarm, CHECK_INTERVAL_MS);
                    }
                },
                delayMs);
    }

    @Override
    public void dispose() {
        // Nothing to dispose of.
    }
}
