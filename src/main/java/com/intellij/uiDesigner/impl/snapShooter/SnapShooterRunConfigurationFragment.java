/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.impl.snapShooter;

import com.intellij.java.execution.impl.application.ApplicationConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.fragment.TagRunConfigurationFragment;
import consulo.localize.LocalizeValue;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;

/**
 * Starts an application through the snap shooter, so forms can be captured from the running application.
 */
@ExtensionImpl
public class SnapShooterRunConfigurationFragment implements TagRunConfigurationFragment<RunConfiguration> {
    @Override
    public String getId() {
        return "snapShooter";
    }

    @Override
    public LocalizeValue getName() {
        return UIDesignerLocalize.runConfigurationFragmentSnapshooter();
    }

    @Override
    public Class<SnapShooterFragmentState> getStateClass() {
        return SnapShooterFragmentState.class;
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof ApplicationConfiguration;
    }

    @Override
    public boolean isSet(RunConfiguration target) {
        return isEnabled((RunConfigurationBase) target);
    }

    @Override
    public void set(RunConfiguration target, boolean value) {
        setEnabled((RunConfigurationBase) target, value);
    }

    @Override
    public void convertLegacyState(RunConfiguration configuration, LegacyRunConfigurationConverter converter) {
        if (converter.readXml(SnapShooterLegacyState.class).ENABLE_SWING_INSPECTOR) {
            converter.addFragment(new SnapShooterFragmentState(true));
        }
    }

    public static boolean isEnabled(RunConfigurationBase configuration) {
        return configuration.getFragmentState(SnapShooterFragmentState.class).isEnabled();
    }

    public static void setEnabled(RunConfigurationBase configuration, boolean enabled) {
        configuration.getFragmentState(SnapShooterFragmentState.class).setEnabled(enabled);
    }
}
