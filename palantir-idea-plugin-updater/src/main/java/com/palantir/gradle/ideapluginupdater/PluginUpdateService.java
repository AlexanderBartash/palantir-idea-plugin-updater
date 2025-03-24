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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.PluginUpdateFacadeKt;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Updates <a href="https://plugins.jetbrains.com/vendor/05482c17-76bf-4895-ab20-d5237ceb53a7">Palantir plugins</a>
 * in the IntelliJ IDEs.
 *
 * @author AlexanderBartash@gmail.com
 */
public class PluginUpdateService {
    /** See {@code resources/i18n/PalantirBundle.properties}. */
    public static final String PLUGIN_UPDATES_NOTIFICATION_GROUP_ID = "notifications.group.palantir.plugins.updates";

    public static PluginUpdateService getInstance(final @NotNull Project project) {
        return project.getService(PluginUpdateService.class);
    }

    private final @NotNull Project project;

    @Contract(pure = true)
    public PluginUpdateService(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Checks for updates of the
     * <a href="https://plugins.jetbrains.com/vendor/05482c17-76bf-4895-ab20-d5237ceb53a7">Palantir plugins</a>
     * in the JetBrains Marketplace and updates them if necessary.
     * After the successful update a popup with an {@link #suggestIdeRestartIfNeeded IDE restart} suggestion is shown.
     */
    @RequiresBackgroundThread
    public void updatePluginsIfNeeded() {
        if (this.project.isDisposed()) {
            return;
        }

        final Set<PluginId> outdatedPalantirPluginIds = this.getOutdatedPalantirPluginIds();
        if (outdatedPalantirPluginIds.isEmpty()) {
            return;
        }
        if (this.project.isDisposed()) {
            return;
        }

        this.updatePlugins(outdatedPalantirPluginIds);
    }

    @NotNull
    private Set<PluginId> getOutdatedPalantirPluginIds() {
        WriteAction.runAndWait(() -> {
            // Not really sure if we need this, but should not hurt.
            final UpdateSettings updateSettings = UpdateSettings.getInstance();
            updateSettings.setCheckNeeded(true);
            updateSettings.setPluginsCheckNeeded(true);
            updateSettings.forceCheckForUpdateAfterRestart();
        });

        final Collection<PluginDownloader> pendingUpdates = PluginUpdateFacadeKt.getPendingUpdates();
        if (null == pendingUpdates || pendingUpdates.isEmpty()) {
            return Collections.emptySet();
        }

        return pendingUpdates.stream()
                .filter(it -> it.getDescriptor().getVendor() != null)
                .filter(it -> it.getDescriptor().getVendor().contains("Palantir"))
                .map(PluginDownloader::getId)
                .collect(Collectors.toSet());
    }

    private void updatePlugins(final @NotNull Set<PluginId> outdatedPalantirPluginIds) {
        // Either installs or enables the plugins with handling all corner cases.
        // https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#plugin-suggestions
        PluginsAdvertiser.installAndEnable(
                this.project, outdatedPalantirPluginIds, false, false, null, this::suggestIdeRestartIfNeeded);
    }

    private void suggestIdeRestartIfNeeded() {
        if (this.project.isDisposed()) {
            return;
        }

        // Just in case, this does not work:
        // InstalledPluginsState.getInstance().isRestartRequired()
        //
        // And this does not work either:
        // final boolean restartNotRequired = outdatedPalantirPluginIds.stream().noneMatch(pluginId -> {
        //     final IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
        //     return null != pluginDescriptor && pluginDescriptor.isRequireRestart();
        // });
        //
        // if (restartNotRequired) return;
        //
        // To be honest, it is better to suggest the IDE restart always, even if the above checks worked,
        // since the dynamic plugin loading/unloading is not perfect and may cause issues with classes.
        //
        // Also, since at the moment all the Palantir plugins except this one have require-restart="true",
        // there is no harm in suggesting a restart after each update.

        final NotificationGroupManager notificationGroupManager = NotificationGroupManager.getInstance();
        final NotificationGroup group =
                notificationGroupManager.getNotificationGroup(PLUGIN_UPDATES_NOTIFICATION_GROUP_ID);

        final PalantirI18nBundle i18nBundle = PalantirI18nBundle.getInstance();
        final Notification notification = group.createNotification(
                i18nBundle.message("notifications.message.ide.restart.needed"), NotificationType.INFORMATION);

        final NotificationAction action = NotificationAction.createSimple(
                i18nBundle.message("notifications.message.restart.ide"), () -> this.restartIdeAsync(false));
        notification.addAction(action);

        Notifications.Bus.notify(notification);
    }

    @SuppressWarnings("SameParameterValue")
    private void restartIdeAsync(final boolean exitConfirmed) {
        // Without the invokeLater it logs an exception complaining about a memory leak on exit.
        // A write action is not required because it leads to an error: AWT events are not allowed inside write action.
        ApplicationManager.getApplication().invokeLater(() -> {
            if (this.project.isDisposed()) {
                return;
            }
            final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
            app.restart(exitConfirmed);
        });
    }
}
