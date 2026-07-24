/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadVSpacer;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import jakarta.inject.Singleton;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class HSizePolicyProperty extends SizePolicyProperty
{
	public static HSizePolicyProperty getInstance(Project project)
	{
		return ServiceManager.getService(project, HSizePolicyProperty.class);
	}

	public HSizePolicyProperty()
	{
		super("Horizontal Size Policy");
	}

	protected int getValueImpl(GridConstraints constraints)
	{
		return constraints.getHSizePolicy();
	}

	protected void setValueImpl(GridConstraints constraints, int policy)
	{
		constraints.setHSizePolicy(policy);
	}

	@Override
	public boolean appliesTo(RadComponent component)
	{
		return !(component instanceof RadVSpacer);
	}
}
