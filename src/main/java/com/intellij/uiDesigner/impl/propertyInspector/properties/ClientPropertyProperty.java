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

import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.AbstractTextFieldEditor;
import com.intellij.uiDesigner.impl.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.impl.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.impl.propertyInspector.editors.PrimitiveTypeEditor;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class ClientPropertyProperty extends Property
{
	private final PropertyRenderer myRenderer;
	private PropertyEditor myEditor;

	public ClientPropertyProperty(Property parent, String name, String valueClass)
	{
		super(parent, name);
		if(valueClass.equals(Boolean.class.getName()))
		{
			myRenderer = new BooleanRenderer();
			myEditor = new BooleanEditor();
		}
		else if(valueClass.equals(Double.class.getName()))
		{
			myRenderer = new LabelPropertyRenderer();
			myEditor = new PrimitiveTypeEditor(Double.class);
		}
		else
		{
			myRenderer = new LabelPropertyRenderer();
			if(valueClass.equals(Integer.class.getName()))
			{
				myEditor = new IntEditor(Integer.MIN_VALUE);
			}
			else if(valueClass.equals(String.class.getName()))
			{
				myEditor = new AbstractTextFieldEditor<String>()
				{
					public String getValue() throws Exception
					{
						return myTf.getText();
					}
				};
			}
		}
	}

	public Object getValue(RadComponent component)
	{
		return component.getDelegee().getClientProperty(getName());
	}

	protected void setValueImpl(RadComponent component, Object value) throws Exception
	{
		component.getDelegee().putClientProperty(getName(), value);
	}

	@Override
	public boolean isModified(RadComponent component)
	{
		return getValue(component) != null;
	}

	@Override
	public void resetValue(RadComponent component) throws Exception
	{
		component.getDelegee().putClientProperty(getName(), null);
	}

	@Nonnull
	public PropertyRenderer getRenderer()
	{
		return myRenderer;
	}

	public PropertyEditor getEditor()
	{
		return myEditor;
	}
}
