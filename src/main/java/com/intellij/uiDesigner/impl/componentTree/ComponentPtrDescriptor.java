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
package com.intellij.uiDesigner.impl.componentTree;

import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ComponentPtrDescriptor extends NodeDescriptor<ComponentPtr>
{
	private ComponentPtr myPtr;
	/**
	 * RadComponent.getBinding() or RadRootContainer.getClassToBind()
	 */
	private String myBinding;
	private String myTitle;

	public ComponentPtrDescriptor(@Nonnull NodeDescriptor parentDescriptor, @Nonnull ComponentPtr ptr)
	{
		super(parentDescriptor);

		myPtr = ptr;
	}

	@Override
    @RequiredUIAccess
    public boolean update()
	{
		myPtr.validate();
		if(!myPtr.isValid())
		{
			myPtr = null;
			return true;
		}

		String oldBinding = myBinding;
		String oldTitle = myTitle;
		RadComponent component = myPtr.getComponent();
		if(component.getModule().isDisposed())
		{
			return false;
		}
		if(component instanceof RadRootContainer)
		{
			myBinding = ((RadRootContainer) component).getClassToBind();
		}
		else
		{
			myBinding = component.getBinding();
		}
		myTitle = component.getComponentTitle();
		return !Comparing.equal(oldBinding, myBinding) || !Comparing.equal(oldTitle, myTitle);
	}

	@Nullable
	public RadComponent getComponent()
	{
		return myPtr != null ? myPtr.getComponent() : null;
	}

	@Override
    public ComponentPtr getElement()
	{
		return myPtr;
	}
}
