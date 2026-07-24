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

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import java.awt.*;

/**
 * This is "synthetic" property of the RadContainer.
 * It represets margins of GridLayoutManager. <b>Note, that
 * this property exists only in RadContainer</b>.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 * @see GridLayoutManager#getMargin
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class MarginProperty extends AbstractInsetsProperty<RadContainer>
{
	private static final Insets DEFAULT_INSETS = new Insets(0, 0, 0, 0);

	public static MarginProperty getInstance(Project project)
	{
		return ServiceManager.getService(project, MarginProperty.class);
	}

	public MarginProperty()
	{
		super("margins");
	}

	public Insets getValue(RadContainer component)
	{
		if(component.getLayout() instanceof AbstractLayout)
		{
			AbstractLayout layoutManager = (AbstractLayout) component.getLayout();
			return layoutManager.getMargin();
		}
		return DEFAULT_INSETS;
	}

	protected void setValueImpl(RadContainer component, @Nonnull Insets value) throws Exception
	{
		AbstractLayout layoutManager = (AbstractLayout) component.getLayout();
		layoutManager.setMargin(value);
	}

	@Override
	public boolean isModified(RadContainer component)
	{
		return !getValue(component).equals(DEFAULT_INSETS);
	}

	@Override
	public void resetValue(RadContainer component) throws Exception
	{
		setValueImpl(component, DEFAULT_INSETS);
	}
}
