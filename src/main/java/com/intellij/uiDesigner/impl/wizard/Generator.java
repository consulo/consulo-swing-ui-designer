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
package com.intellij.uiDesigner.impl.wizard;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.PsiPropertiesProvider;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.fileEditor.FileEditorManager;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class Generator
{
	private static final Logger LOG = Logger.getInstance(Generator.class);

	private Generator()
	{
	}

	/**
	 * @param rootContainer output parameter; should be LwRootContainer[1]
	 */
	public static FormProperty[] exposeForm(Project project, VirtualFile formFile, LwRootContainer[] rootContainer) throws MyException
	{
		Module module = ModuleUtilCore.findModuleForFile(formFile, project);
		LOG.assertTrue(module != null);

		PsiPropertiesProvider propertiesProvider = new PsiPropertiesProvider(module);

		Document doc = FileDocumentManager.getInstance().getDocument(formFile);
		LwRootContainer _rootContainer;
		try
		{
			_rootContainer = Utils.getRootContainer(doc.getText(), propertiesProvider);
		}
		catch(AlienFormFileException e)
		{
			throw new MyException(e.getMessage());
		}
		catch(Exception e)
		{
			throw new MyException(UIDesignerBundle.message("error.cannot.process.form.file", e));
		}

		rootContainer[0] = _rootContainer;

		final String classToBind = _rootContainer.getClassToBind();
		if(classToBind == null)
		{
			throw new MyException(UIDesignerBundle.message("error.form.is.not.bound.to.a.class"));
		}

		final PsiClass boundClass = FormEditingUtil.findClassToBind(module, classToBind);
		if(boundClass == null)
		{
			throw new MyException(UIDesignerBundle.message("error.bound.class.does.not.exist", classToBind));
		}

		final ArrayList<FormProperty> result = new ArrayList<>();
		final MyException[] exception = new MyException[1];

		FormEditingUtil.iterate(
				_rootContainer,
				new FormEditingUtil.ComponentVisitor<LwComponent>()
				{
					public boolean visit(LwComponent component)
					{
						String binding = component.getBinding();
						if(binding == null)
						{
							return true;
						}

						PsiField[] fields = boundClass.getFields();
						PsiField field = null;
						for(int i = fields.length - 1; i >= 0; i--)
						{
							if(binding.equals(fields[i].getName()))
							{
								field = fields[i];
								break;
							}
						}
						if(field == null)
						{
							exception[0] = new MyException(UIDesignerBundle.message("error.field.not.found.in.class", binding, classToBind));
							return false;
						}

						PsiClass fieldClass = getClassByType(field.getType());
						if(fieldClass == null)
						{
							exception[0] = new MyException(UIDesignerBundle.message("error.invalid.binding.field.type", binding, classToBind));
							return false;
						}

						if(instanceOf(fieldClass, JTextComponent.class.getName()))
						{
							result.add(new FormProperty(component, "getText", "setText", String.class.getName()));
						}
						else if(instanceOf(fieldClass, JCheckBox.class.getName()))
						{
							result.add(new FormProperty(component, "isSelected", "setSelected", boolean.class.getName()));
						}

						return true;
					}
				}
		);

		if(exception[0] != null)
		{
			throw exception[0];
		}

		return result.toArray(new FormProperty[result.size()]);
	}

	private static PsiClass getClassByType(PsiType type)
	{
		if(!(type instanceof PsiClassType))
		{
			return null;
		}
		return ((PsiClassType) type).resolve();
	}

	private static boolean instanceOf(PsiClass jComponentClass, String baseClassName)
	{
		for(PsiClass c = jComponentClass; c != null; c = c.getSuperClass())
		{
			if(baseClassName.equals(c.getQualifiedName()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Should be invoked in command and write action
	 */
	@SuppressWarnings({"HardCodedStringLiteral"})
	public static void generateDataBindingMethods(WizardData data) throws MyException
	{
		if(data.myBindToNewBean)
		{
			data.myBeanClass = createBeanClass(data);
		}
		else
		{
			if(!CommonRefactoringUtil.checkReadOnlyStatus(data.myBeanClass.getProject(), data.myBeanClass))
			{
				return;
			}
		}

		HashMap<String, String> binding2beanGetter = new HashMap<>();
		HashMap<String, String> binding2beanSetter = new HashMap<>();

		FormProperty2BeanProperty[] bindings = data.myBindings;
		for(FormProperty2BeanProperty form2bean : bindings)
		{
			if(form2bean == null || form2bean.myBeanProperty == null)
			{
				continue;
			}

			// check that bean contains the property, and if not, try to add the property to the bean
			{
				String setterName = PropertyUtil.suggestSetterName(form2bean.myBeanProperty.myName);
				PsiMethod[] methodsByName = data.myBeanClass.findMethodsByName(setterName, true);
				if(methodsByName.length < 1)
				{
					// bean does not contain this property
					// try to add...

					LOG.assertTrue(!data.myBindToNewBean); // just generated bean class should contain all necessary properties

					if(!data.myBeanClass.isWritable())
					{
						throw new MyException("Cannot add property to non writable class " + data.myBeanClass.getQualifiedName());
					}

					StringBuffer membersBuffer = new StringBuffer();
					StringBuffer methodsBuffer = new StringBuffer();

					Project project = data.myBeanClass.getProject();
					CodeStyleManager formatter = CodeStyleManager.getInstance(project);
					JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(project);

					generateProperty(styler, form2bean.myBeanProperty.myName, form2bean.myBeanProperty.myType, membersBuffer,
							methodsBuffer);

					PsiClass fakeClass;
					try
					{
						fakeClass = JavaPsiFacade.getInstance(data.myBeanClass.getProject()).getElementFactory()
								.createClassFromText(membersBuffer.toString() + methodsBuffer.toString(), null);

						PsiField[] fields = fakeClass.getFields();
						{
							PsiElement result = data.myBeanClass.add(fields[0]);
							styler.shortenClassReferences(result);
							formatter.reformat(result);
						}

						PsiMethod[] methods = fakeClass.getMethods();
						{
							PsiElement result = data.myBeanClass.add(methods[0]);
							styler.shortenClassReferences(result);
							formatter.reformat(result);
						}
						{
							PsiElement result = data.myBeanClass.add(methods[1]);
							styler.shortenClassReferences(result);
							formatter.reformat(result);
						}
					}
					catch(IncorrectOperationException e)
					{
						throw new MyException(e.getMessage());
					}
				}
			}

			PsiMethod propertySetter = PropertyUtil.findPropertySetter(data.myBeanClass, form2bean.myBeanProperty.myName, false, true);
			PsiMethod propertyGetter = PropertyUtil.findPropertyGetter(data.myBeanClass, form2bean.myBeanProperty.myName, false, true);

			if(propertyGetter == null)
			{
				// todo
				continue;
			}
			if(propertySetter == null)
			{
				// todo
				continue;
			}

			String binding = form2bean.myFormProperty.getLwComponent().getBinding();
			binding2beanGetter.put(binding, propertyGetter.getName());
			binding2beanSetter.put(binding, propertySetter.getName());
		}

		String dataBeanClassName = data.myBeanClass.getQualifiedName();

		LwRootContainer[] rootContainer = new LwRootContainer[1];
		FormProperty[] formProperties = exposeForm(data.myProject, data.myFormFile, rootContainer);

		StringBuffer getDataBody = new StringBuffer();
		StringBuffer setDataBody = new StringBuffer();
		StringBuffer isModifiedBody = new StringBuffer();

		// iterate exposed formproperties

		for(FormProperty formProperty : formProperties)
		{
			String binding = formProperty.getLwComponent().getBinding();
			if(!binding2beanGetter.containsKey(binding))
			{
				continue;
			}

			getDataBody.append("data.");
			getDataBody.append(binding2beanSetter.get(binding));
			getDataBody.append("(");
			getDataBody.append(binding);
			getDataBody.append(".");
			getDataBody.append(formProperty.getComponentPropertyGetterName());
			getDataBody.append("());\n");

			setDataBody.append(binding);
			setDataBody.append(".");
			setDataBody.append(formProperty.getComponentPropertySetterName());
			setDataBody.append("(data.");
			setDataBody.append(binding2beanGetter.get(binding));
			setDataBody.append("());\n");

			String propertyClassName = formProperty.getComponentPropertyClassName();
			if("boolean".equals(propertyClassName))
			{
				isModifiedBody.append("if (");
				//
				isModifiedBody.append(binding);
				isModifiedBody.append(".");
				isModifiedBody.append(formProperty.getComponentPropertyGetterName());
				isModifiedBody.append("()");
				//
				isModifiedBody.append("!= ");
				//
				isModifiedBody.append("data.");
				isModifiedBody.append(binding2beanGetter.get(binding));
				isModifiedBody.append("()");
				//
				isModifiedBody.append(") return true;\n");
			}
			else
			{
				isModifiedBody.append("if (");
				//
				isModifiedBody.append(binding);
				isModifiedBody.append(".");
				isModifiedBody.append(formProperty.getComponentPropertyGetterName());
				isModifiedBody.append("()");
				//
				isModifiedBody.append("!= null ? ");
				//
				isModifiedBody.append("!");
				//
				isModifiedBody.append(binding);
				isModifiedBody.append(".");
				isModifiedBody.append(formProperty.getComponentPropertyGetterName());
				isModifiedBody.append("()");
				//
				isModifiedBody.append(".equals(");
				//
				isModifiedBody.append("data.");
				isModifiedBody.append(binding2beanGetter.get(binding));
				isModifiedBody.append("()");
				isModifiedBody.append(") : ");
				//
				isModifiedBody.append("data.");
				isModifiedBody.append(binding2beanGetter.get(binding));
				isModifiedBody.append("()");
				isModifiedBody.append("!= null");
				//
				isModifiedBody.append(") return true;\n");
			}
		}
		isModifiedBody.append("return false;\n");

		String textOfMethods =
				"public void setData(" + dataBeanClassName + " data){\n" +
						setDataBody.toString() +
						"}\n" +
						"\n" +
						"public void getData(" + dataBeanClassName + " data){\n" +
						getDataBody.toString() +
						"}\n" +
						"\n" +
						"public boolean isModified(" + dataBeanClassName + " data){\n" +
						isModifiedBody.toString() +
						"}\n";

		// put them to the bound class

		Module module = ModuleUtilCore.findModuleForFile(data.myFormFile, data.myProject);
		LOG.assertTrue(module != null);
		PsiClass boundClass = FormEditingUtil.findClassToBind(module, rootContainer[0].getClassToBind());
		LOG.assertTrue(boundClass != null);

		if(!CommonRefactoringUtil.checkReadOnlyStatus(module.getProject(), boundClass))
		{
			return;
		}

		// todo: check that this method does not exist yet

		PsiClass fakeClass;
		try
		{
			fakeClass = JavaPsiFacade.getInstance(data.myProject).getElementFactory().createClassFromText(textOfMethods, null);

			PsiMethod methodSetData = fakeClass.getMethods()[0];
			PsiMethod methodGetData = fakeClass.getMethods()[1];
			PsiMethod methodIsModified = fakeClass.getMethods()[2];

			PsiMethod existing1 = boundClass.findMethodBySignature(methodSetData, false);
			PsiMethod existing2 = boundClass.findMethodBySignature(methodGetData, false);
			PsiMethod existing3 = boundClass.findMethodBySignature(methodIsModified, false);

			// warning already shown
			if(existing1 != null)
			{
				existing1.delete();
			}
			if(existing2 != null)
			{
				existing2.delete();
			}
			if(existing3 != null)
			{
				existing3.delete();
			}

			CodeStyleManager formatter = CodeStyleManager.getInstance(module.getProject());
			JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(module.getProject());

			PsiElement setData = boundClass.add(methodSetData);
			styler.shortenClassReferences(setData);
			formatter.reformat(setData);

			PsiElement getData = boundClass.add(methodGetData);
			styler.shortenClassReferences(getData);
			formatter.reformat(getData);

			if(data.myGenerateIsModified)
			{
				PsiElement isModified = boundClass.add(methodIsModified);
				styler.shortenClassReferences(isModified);
				formatter.reformat(isModified);
			}

			OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(setData.getProject()).builder(setData.getContainingFile().getVirtualFile()).offset(setData.getTextOffset())
					.build();
			FileEditorManager.getInstance(data.myProject).openTextEditor(descriptor, true);
		}
		catch(IncorrectOperationException e)
		{
			throw new MyException(e.getMessage());
		}
	}

	@Nonnull
	private static PsiClass createBeanClass(WizardData wizardData) throws MyException
	{
		PsiManager psiManager = PsiManager.getInstance(wizardData.myProject);

		ProjectRootManager projectRootManager = ProjectRootManager.getInstance(wizardData.myProject);
		ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
		VirtualFile sourceRoot = fileIndex.getSourceRootForFile(wizardData.myFormFile);
		if(sourceRoot == null)
		{
			throw new MyException(UIDesignerBundle.message("error.form.file.is.not.in.source.root"));
		}

		PsiDirectory rootDirectory = psiManager.findDirectory(sourceRoot);
		LOG.assertTrue(rootDirectory != null);

		PsiJavaPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(wizardData.myPackageName);
		if(aPackage == null)
		{
			throw new MyException(UIDesignerBundle.message("error.package.does.not.exist", wizardData.myPackageName));
		}

		PsiDirectory targetDir = null;

		PsiDirectory[] directories = aPackage.getDirectories();
		for(PsiDirectory psiDirectory : directories)
		{
			if(PsiTreeUtil.isAncestor(rootDirectory, psiDirectory, false))
			{
				targetDir = psiDirectory;
				break;
			}
		}

		if(targetDir == null)
		{
			// todo
			throw new MyException(UIDesignerBundle.message("error.cannot.find.package", wizardData.myPackageName));
		}

		//noinspection HardCodedStringLiteral
		String body =
				"public class " + wizardData.myShortClassName + "{\n" +
						"public " + wizardData.myShortClassName + "(){}\n" +
						"}";

		try
		{
			PsiFile sourceFile =
					PsiFileFactory.getInstance(psiManager.getProject()).createFileFromText(wizardData.myShortClassName + ".java", body);
			sourceFile = (PsiFile) targetDir.add(sourceFile);

			PsiClass beanClass = ((PsiJavaFile) sourceFile).getClasses()[0];

			ArrayList<String> properties = new ArrayList<>();
			HashMap<String, String> property2fqClassName = new HashMap<>();

			FormProperty2BeanProperty[] bindings = wizardData.myBindings;
			for(FormProperty2BeanProperty binding : bindings)
			{
				if(binding == null || binding.myBeanProperty == null)
				{
					continue;
				}

				properties.add(binding.myBeanProperty.myName);

				// todo: handle "casts" ?

				String propertyClassName = binding.myFormProperty.getComponentPropertyClassName();

				property2fqClassName.put(binding.myBeanProperty.myName, propertyClassName);
			}

			generateBean(beanClass, ArrayUtil.toStringArray(properties), property2fqClassName);

			return beanClass;
		}
		catch(consulo.language.util.IncorrectOperationException e)
		{
			throw new MyException(e.getMessage());
		}
	}

	// todo: inline
	private static void generateBean(
			PsiClass aClass,
			String[] properties,
			HashMap<String, String> property2fqClassName
	) throws MyException
	{
		StringBuffer membersBuffer = new StringBuffer();
		StringBuffer methodsBuffer = new StringBuffer();

		CodeStyleManager formatter = CodeStyleManager.getInstance(aClass.getProject());
		JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(aClass.getProject());

		for(String property : properties)
		{
			LOG.assertTrue(property != null);
			String type = property2fqClassName.get(property);
			LOG.assertTrue(type != null);

			generateProperty(styler, property, type, membersBuffer, methodsBuffer);
		}

		PsiClass fakeClass;
		try
		{
			fakeClass = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createClassFromText(
					membersBuffer.toString() + methodsBuffer.toString(),
					null
			);

			PsiField[] fields = fakeClass.getFields();
			for(PsiField field : fields)
			{
				aClass.add(field);
			}

			PsiMethod[] methods = fakeClass.getMethods();
			for(PsiMethod method : methods)
			{
				aClass.add(method);
			}

			styler.shortenClassReferences(aClass);
			formatter.reformat(aClass);
		}
		catch(consulo.language.util.IncorrectOperationException e)
		{
			throw new MyException(e.getMessage());
		}
	}

	private static void generateProperty(JavaCodeStyleManager codeStyleManager,
                                         String property,
                                         String type,
                                         @NonNls StringBuffer membersBuffer, @NonNls StringBuffer methodsBuffer)
	{
		String field = codeStyleManager.suggestVariableName(VariableKind.FIELD, property, null, null).names[0];

		membersBuffer.append("private ");
		membersBuffer.append(type);
		membersBuffer.append(" ");
		membersBuffer.append(field);
		membersBuffer.append(";\n");

		// getter
		methodsBuffer.append("public ");
		methodsBuffer.append(type);
		methodsBuffer.append(" ");
		methodsBuffer.append(suggestGetterName(property, type));
		methodsBuffer.append("(){\n");
		methodsBuffer.append("return ");
		methodsBuffer.append(field);
		methodsBuffer.append(";}\n");

		// setter
		String parameterName = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, property, null, null).names[0];
		methodsBuffer.append("public void ");
		methodsBuffer.append(PropertyUtil.suggestSetterName(property));
		methodsBuffer.append("(final ");
		methodsBuffer.append(type);
		methodsBuffer.append(" ");
		methodsBuffer.append(parameterName);
		methodsBuffer.append("){\n");
		if(parameterName.equals(field))
		{
			methodsBuffer.append("this.");
		}
		methodsBuffer.append(field);
		methodsBuffer.append("=");
		methodsBuffer.append(parameterName);
		methodsBuffer.append(";}\n");
	}

	@SuppressWarnings({"HardCodedStringLiteral"})
	private static String suggestGetterName(String propertyName, String propertyType)
	{
		StringBuffer name = new StringBuffer(StringUtil.capitalize(propertyName));
		if("boolean".equals(propertyType))
		{
			name.insert(0, "is");
		}
		else
		{
			name.insert(0, "get");
		}
		return name.toString();
	}

	public static void prepareWizardData(WizardData data, PsiClass boundClass) throws MyException
	{

		PsiMethod[] allGetDataMethods = boundClass.findMethodsByName("getData", false);
		PsiMethod[] allSetDataMethods = boundClass.findMethodsByName("setData", false);

		PsiMethod setDataMethod = null;
		PsiClass beanClass = null;

		// find get/set pair and bean class
		outer:
		for(int i = 0; i < allGetDataMethods.length; i++)
		{
			PsiMethod _getMethod = allGetDataMethods[i];

			if(_getMethod.getReturnType() != PsiType.VOID)
			{
				continue;
			}

			PsiParameter[] _getMethodParameters = _getMethod.getParameterList().getParameters();
			if(_getMethodParameters.length != 1)
			{
				continue;
			}

			PsiClass _getParameterClass = getClassByType(_getMethodParameters[0].getType());
			if(_getParameterClass == null)
			{
				continue;
			}

			for(PsiMethod _setMethod : allSetDataMethods)
			{
				if(_setMethod.getReturnType() != PsiType.VOID)
				{
					continue;
				}

				PsiParameter[] _setMethodParameters = _setMethod.getParameterList().getParameters();
				if(_setMethodParameters.length != 1)
				{
					continue;
				}

				PsiClass _setParameterClass = getClassByType(_setMethodParameters[0].getType());
				if(_setParameterClass != _getParameterClass)
				{
					continue;
				}

				// pair found !!!

				setDataMethod = _setMethod;
				beanClass = _getParameterClass;
				break outer;
			}
		}

		if(beanClass == null)
		{
			// nothing found
			return;
		}

		data.myBindToNewBean = false;
		data.myBeanClass = beanClass;

		// parse setData() and try to associate fields with bean
		{
			PsiCodeBlock body = setDataMethod.getBody();
			if(body == null)
			{
				return;
			}

			PsiElement[] children = body.getChildren();
			for(PsiElement child : children)
			{
				// Parses sequences like: a.foo(b.bar());
				PsiField bindingField;

				if(!(child instanceof PsiExpressionStatement))
				{
					continue;
				}

				PsiExpression expression = ((PsiExpressionStatement) child).getExpression();
				if(!(expression instanceof PsiMethodCallExpression))
				{
					continue;
				}

				PsiMethodCallExpression callExpression = (PsiMethodCallExpression) expression;

				// find binding field ('a')
				int index = -1;
				{
					PsiElement psiElement = getObjectForWhichMethodWasCalled(callExpression);
					if(!(psiElement instanceof PsiField))
					{
						continue;
					}

					if(((PsiField) psiElement).getContainingClass() != boundClass)
					{
						continue;
					}

					bindingField = (PsiField) psiElement;

					// find binding for this field
					FormProperty2BeanProperty[] bindings = data.myBindings;
					for(int j = 0; j < bindings.length; j++)
					{
						FormProperty2BeanProperty binding = bindings[j];
						if(bindingField.getName().equals(binding.myFormProperty.getLwComponent().getBinding()))
						{
							index = j;
							break;
						}
					}
				}

				if(index == -1)
				{
					continue;
				}

				// find 'bar()'
				{
					PsiReferenceParameterList parameterList = callExpression.getMethodExpression().getParameterList();
					if(parameterList == null)
					{
						continue;
					}

					PsiExpressionList argumentList = callExpression.getArgumentList();
					if(argumentList == null)
					{
						continue;
					}

					PsiExpression[] expressions = argumentList.getExpressions();
					if(expressions == null || expressions.length != 1)
					{
						continue;
					}

					if(!(expressions[0] instanceof PsiMethodCallExpression))
					{
						continue;
					}

					PsiMethodCallExpression callExpression2 = ((PsiMethodCallExpression) expressions[0]);

					// check that 'b' is parameter
					PsiElement psiElement = getObjectForWhichMethodWasCalled(callExpression2);
					if(!(psiElement instanceof PsiParameter))
					{
						continue;
					}

					PsiMethod barMethod = ((PsiMethod) callExpression2.getMethodExpression().resolve());
					if(barMethod == null)
					{
						continue;
					}

					if(!PropertyUtil.isSimplePropertyGetter(barMethod))
					{
						continue;
					}

					String propertyName = PropertyUtil.getPropertyName(barMethod);

					// There are two possible types: boolean and java.lang.String
					String typeName = barMethod.getReturnType().getCanonicalText();
					if(!"boolean".equals(typeName) && !"java.lang.String".equals(typeName))
					{
						continue;
					}

					data.myBindings[index].myBeanProperty = new BeanProperty(propertyName, typeName);
				}
			}
		}
	}

	private static PsiElement getObjectForWhichMethodWasCalled(PsiMethodCallExpression callExpression)
	{
		PsiExpression qualifierExpression = callExpression.getMethodExpression().getQualifierExpression();
		if(!(qualifierExpression instanceof PsiReferenceExpression))
		{
			return null;
		}
		return ((PsiReferenceExpression) qualifierExpression).resolve();
	}

	public static final class MyException extends Exception
	{
		public MyException(String message)
		{
			super(message);
		}
	}
}
