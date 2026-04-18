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
package com.intellij.uiDesigner.impl.make;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.java.language.impl.projectRoots.JavaSdkVersionUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.GuiDesignerConfiguration;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.CompilerUtil;
import consulo.content.bundle.Sdk;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.internal.org.objectweb.asm.ClassWriter;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.DirectoryIndex;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.Chunk;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.util.lang.ExceptionUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@ExtensionImpl
public final class Form2ByteCodeCompiler implements ClassInstrumentingCompiler
{
	private static final String CLASS_SUFFIX = ".class";
	private static final Logger LOG = Logger.getInstance(Form2ByteCodeCompiler.class);

	@Override
	@Nonnull
	public String getDescription()
	{
		return UIDesignerBundle.message("component.gui.designer.form.to.bytecode.compiler");
	}

	@Override
	public boolean validateConfiguration(CompileScope scope)
	{
		return true;
	}

	@Nonnull
	public static InstrumentationClassFinder createClassFinder(@Nonnull final String classPath)
	{
		final ArrayList<URL> urls = new ArrayList<>();
		for(StringTokenizer tokenizer = new StringTokenizer(classPath, File.pathSeparator); tokenizer.hasMoreTokens(); )
		{
			final String s = tokenizer.nextToken();
			try
			{
				urls.add(new File(s).toURI().toURL());
			}
			catch(Exception exc)
			{
				throw new RuntimeException(exc);
			}
		}
		return new InstrumentationClassFinder(urls.toArray(new URL[urls.size()]));
	}

	@Nonnull
	public static InstrumentationClassFinder createClassFinder(@Nonnull CompileContext context, @Nonnull final Module module)
	{
		ModuleChunk moduleChunk = new ModuleChunk((CompileContextEx) context, new Chunk<>(module), Collections.<Module, List<VirtualFile>>emptyMap());

		Set<VirtualFile> compilationBootClasspath = JavaCompilerUtil.getCompilationBootClasspath(context, moduleChunk);
		Set<VirtualFile> compilationClasspath = JavaCompilerUtil.getCompilationClasspath(context, moduleChunk);

		URL[] platformUrls = toUrls(compilationBootClasspath);

		Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
		if(sdk != null && JavaSdkVersionUtil.getJavaSdkVersion(sdk).isAtLeast(JavaSdkVersion.JDK_1_9))
		{
			try
			{
				platformUrls = ArrayUtil.append(platformUrls, InstrumentationClassFinder.createJDKPlatformUrl(sdk.getHomePath()));
			}
			catch(MalformedURLException ignored)
			{
			}
		}
		return new InstrumentationClassFinder(platformUrls, toUrls(compilationClasspath));
	}

	@Nonnull
	private static URL[] toUrls(Set<VirtualFile> files)
	{
		List<URL> urls = new ArrayList<>(files.size());
		for(VirtualFile file : files)
		{
			try
			{
				File javaFile = VirtualFileUtil.virtualToIoFile(file);
				urls.add(javaFile.getCanonicalFile().toURI().toURL());
			}
			catch(Exception e)
			{
				LOG.error(e);
			}
		}
		return urls.toArray(new URL[urls.size()]);
	}

	@Override
	@Nonnull
	public ProcessingItem[] getProcessingItems(final CompileContext context)
	{
		final Project project = context.getProject();
		if(!GuiDesignerConfiguration.getInstance(project).INSTRUMENT_CLASSES)
		{
			return ProcessingItem.EMPTY_ARRAY;
		}

		final ArrayList<ProcessingItem> items = new ArrayList<>();

		ApplicationManager.getApplication().runReadAction(new Runnable()
		{
			@Override
			public void run()
			{
				final CompileScope scope = context.getCompileScope();
				final CompileScope projectScope = CompilerManager.getInstance(project).createProjectCompileScope();

				final VirtualFile[] formFiles = projectScope.getFiles(GuiFormFileType.INSTANCE);
				if(formFiles.length == 0)
				{
					return;
				}
				final CompilerManager compilerManager = CompilerManager.getInstance(project);
				final BindingsCache bindingsCache = new BindingsCache(project);

				final HashMap<Module, ArrayList<VirtualFile>> module2formFiles = sortByModules(project, formFiles);

				try
				{
					for(final consulo.module.Module module : module2formFiles.keySet())
					{
						final HashMap<String, VirtualFile> class2form = new HashMap<>();

						final ArrayList<VirtualFile> list = module2formFiles.get(module);
						for(final VirtualFile formFile : list)
						{
							if(compilerManager.isExcludedFromCompilation(formFile))
							{
								continue;
							}

							final String classToBind;
							try
							{
								classToBind = bindingsCache.getBoundClassName(formFile);
							}
							catch(AlienFormFileException e)
							{
								// ignore non-IDEA forms
								continue;
							}
							catch(Exception e)
							{
								addMessage(context, UIDesignerBundle.message("error.cannot.process.form.file", ExceptionUtil.getThrowableText(e)), formFile, CompilerMessageCategory.ERROR);
								continue;
							}

							if(classToBind == null)
							{
								continue;
							}

							final File classFile = findFile(context, classToBind, module);
							if(classFile == null)
							{
								if(scope.belongs(formFile.getUrl()))
								{
									addMessage(context, UIDesignerBundle.message("error.class.to.bind.does.not.exist", classToBind), formFile, CompilerMessageCategory.ERROR);
								}
								continue;
							}

							final VirtualFile alreadyProcessedForm = class2form.get(classToBind);
							if(alreadyProcessedForm != null)
							{
								if(belongsToCompileScope(context, formFile, classToBind))
								{
									addMessage(context, UIDesignerBundle.message("error.duplicate.bind", classToBind, alreadyProcessedForm.getPresentableUrl()), formFile, CompilerMessageCategory
											.ERROR);
								}
								continue;
							}
							class2form.put(classToBind, formFile);

							final ProcessingItem item = new MyInstrumentationItem(classFile, formFile, classToBind);
							items.add(item);
						}
					}
				}
				finally
				{
					bindingsCache.close();
				}
			}
		});

		return items.toArray(new ProcessingItem[items.size()]);
	}

	private static boolean belongsToCompileScope(final CompileContext context, final VirtualFile formFile, final String classToBind)
	{
		final CompileScope compileScope = context.getCompileScope();
		if(compileScope.belongs(formFile.getUrl()))
		{
			return true;
		}
		final VirtualFile sourceFile = findSourceFile(context, formFile, classToBind);
		return sourceFile != null && compileScope.belongs(sourceFile.getUrl());
	}

	private static HashMap<Module, ArrayList<VirtualFile>> sortByModules(final Project project, final VirtualFile[] formFiles)
	{
		final HashMap<consulo.module.Module, ArrayList<VirtualFile>> module2formFiles = new HashMap<>();
		for(final VirtualFile formFile : formFiles)
		{
			final consulo.module.Module module = ModuleUtilCore.findModuleForFile(formFile, project);
			if(module != null)
			{
				ArrayList<VirtualFile> list = module2formFiles.get(module);
				if(list == null)
				{
					list = new ArrayList<>();
					module2formFiles.put(module, list);
				}
				list.add(formFile);
			}
		}
		return module2formFiles;
	}

	private static HashMap<Module, ArrayList<MyInstrumentationItem>> sortByModules(final Project project, final ProcessingItem[] items)
	{
		final HashMap<consulo.module.Module, ArrayList<MyInstrumentationItem>> module2formFiles = new HashMap<>();
		for(ProcessingItem item1 : items)
		{
			final MyInstrumentationItem item = (MyInstrumentationItem) item1;
			final VirtualFile formFile = item.getFormFile();

			final Module module = ModuleUtilCore.findModuleForFile(formFile, project);
			if(module != null)
			{
				ArrayList<MyInstrumentationItem> list = module2formFiles.get(module);
				if(list == null)
				{
					list = new ArrayList<>();
					module2formFiles.put(module, list);
				}
				list.add(item);
			}
		}
		return module2formFiles;
	}

	@Nullable
	private static File findFile(final CompileContext context, final String className, final Module module)
	{
		String classPath = className.replace('.', '/');

		File file = findFileByRelativePath(context, module, classPath + CLASS_SUFFIX);
		if(file != null)
		{
			return file;
		}

		int prev = 0;
		while(true)
		{
			int i = classPath.indexOf('/', prev);
			if(i == -1)
			{
				if(prev == 0)
				{
					return findFileByRelativePath(context, module, classPath);
				}
				else
				{
					break;
				}
			}

			prev = i + 1;

			String targetFilePath = classPath.substring(0, i) + CLASS_SUFFIX;
			File targetFile = findFileByRelativePath(context, module, targetFilePath);
			if(targetFile != null)
			{
				String mergedPath = classPath.substring(0, i) + '$' + classPath.substring(i + 1, classPath.length()).replace('/', '$') + CLASS_SUFFIX;
				return findFileByRelativePath(context, module, mergedPath);
			}
		}
		return null;
	}

	@Nullable
	private static File findFileByRelativePath(final CompileContext context, final Module module, final String relativePath)
	{
		VirtualFile output = context.getOutputForFile(module, ProductionContentFolderTypeProvider.getInstance());

		File file = output != null ? getFileByRelativeOrNull(output.getPath(), relativePath) : null;
		if(file == null)
		{
			final VirtualFile testsOutput = context.getOutputForFile(module, TestContentFolderTypeProvider.getInstance());
			if(testsOutput != null && !testsOutput.equals(output))
			{
				file = getFileByRelativeOrNull(testsOutput.getPath(), relativePath);
			}
		}
		return file;
	}

	private static File getFileByRelativeOrNull(String root, String path)
	{
		File file = new File(root, path);
		return file.exists() ? file : null;
	}

	@Override
	public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items)
	{
		final DirectoryIndex directoryIndex = DirectoryIndex.getInstance(context.getProject());
		final List<ProcessingItem> compiledItems = new ArrayList<>();

		context.getProgressIndicator().pushState();
		context.getProgressIndicator().setText(UIDesignerBundle.message("progress.compiling.ui.forms"));

		final Project project = context.getProject();
		final HashMap<consulo.module.Module, ArrayList<MyInstrumentationItem>> module2itemsList = sortByModules(project, items);

		List<File> filesToRefresh = new ArrayList<>();
		for(final consulo.module.Module module : module2itemsList.keySet())
		{
			final InstrumentationClassFinder finder = createClassFinder(context, module);

			try
			{
				GuiDesignerConfiguration designerConfiguration = GuiDesignerConfiguration.getInstance(project);
				if(designerConfiguration.COPY_FORMS_RUNTIME_TO_OUTPUT)
				{
					final String moduleOutputPath = CompilerPaths.getModuleOutputPath(module, ProductionContentFolderTypeProvider.getInstance());
					try
					{
						if(moduleOutputPath != null)
						{
							filesToRefresh.addAll(CopyResourcesUtil.copyFormsRuntime(moduleOutputPath, false));
						}
						final String testsOutputPath = CompilerPaths.getModuleOutputPath(module, TestContentFolderTypeProvider.getInstance());
						if(testsOutputPath != null && !testsOutputPath.equals(moduleOutputPath))
						{
							filesToRefresh.addAll(CopyResourcesUtil.copyFormsRuntime(testsOutputPath, false));
						}
					}
					catch(IOException e)
					{
						addMessage(context, UIDesignerBundle.message("error.cannot.copy.gui.designer.form.runtime", module.getName(), ExceptionUtil.getThrowableText(e)), null,
								CompilerMessageCategory.ERROR);
					}
				}

				final ArrayList<MyInstrumentationItem> list = module2itemsList.get(module);

				for(final MyInstrumentationItem item : list)
				{
					//context.getProgressIndicator().setFraction((double)++formsProcessed / (double)items.length);

					final VirtualFile formFile = item.getFormFile();
					context.getProgressIndicator().setText2(formFile.getPresentableUrl());

					final String text = ApplicationManager.getApplication().runReadAction(new Computable<String>()
					{
						@Override
						public String compute()
						{
							if(!belongsToCompileScope(context, formFile, item.getClassToBindFQname()))
							{
								return null;
							}
							Document document = FileDocumentManager.getInstance().getDocument(formFile);
							return document == null ? null : document.getText();
						}
					});
					if(text == null)
					{
						continue; // does not belong to current scope
					}

					final LwRootContainer rootContainer;
					try
					{
						rootContainer = Utils.getRootContainer(text, new CompiledClassPropertiesProvider(finder.getLoader()));
					}
					catch(Exception e)
					{
						addMessage(context, UIDesignerBundle.message("error.cannot.process.form.file", ExceptionUtil.getThrowableText(e)), formFile, CompilerMessageCategory.ERROR);
						continue;
					}

					if(designerConfiguration.COPY_FORMS_TO_OUTPUT)
					{
						VirtualFile outputForFile = context.getOutputForFile(module, formFile);
						if(outputForFile != null)
						{
							String packageName = directoryIndex.getPackageName(formFile.getParent());

							File outputFormFile;
							if(packageName == null || packageName.isEmpty())
							{
								outputFormFile = new File(outputForFile.getPath(), formFile.getName());
							}
							else
							{
								outputFormFile = new File(outputForFile.getPath(), packageName.replace(".", "/") + "/" + formFile.getName());
							}

							FileUtil.createParentDirs(outputFormFile);
							try
							{
								FileUtil.copy(new File(formFile.getPath()), outputFormFile, FilePermissionCopier.BY_NIO2);
							}
							catch(IOException e)
							{
								addMessage(context, UIDesignerBundle.message("error.cannot.process.form.file", ExceptionUtil.getThrowableText(e)), formFile, CompilerMessageCategory.ERROR);
								continue;
							}
						}
					}

					final File classFile = item.getFile();
					LOG.assertTrue(classFile.exists(), classFile.getPath());

					final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, finder, new PsiNestedFormLoader(module), false, new InstrumenterClassWriter(isJdk6(module) ?
							ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS, finder), designerConfiguration.USE_JB_SCALING);
					ApplicationManager.getApplication().runReadAction(() -> codeGenerator.patchFile(classFile));
					final FormErrorInfo[] errors = codeGenerator.getErrors();
					final FormErrorInfo[] warnings = codeGenerator.getWarnings();
					for(FormErrorInfo warning : warnings)
					{
						addMessage(context, warning, formFile, CompilerMessageCategory.WARNING);
					}
					for(FormErrorInfo error : errors)
					{
						addMessage(context, error, formFile, CompilerMessageCategory.ERROR);
					}
					if(errors.length == 0)
					{
						compiledItems.add(item);
					}
				}
			}
			finally
			{
				finder.releaseResources();
			}
		}
		CompilerUtil.refreshIOFiles(filesToRefresh);
		context.getProgressIndicator().popState();

		return compiledItems.toArray(new ProcessingItem[compiledItems.size()]);
	}

	private static boolean isJdk6(final Module module)
	{
		final Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
		return sdk != null && JavaSdkTypeUtil.isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_6);
	}

	private static void addMessage(final CompileContext context, final String s, final VirtualFile formFile, final CompilerMessageCategory severity)
	{
		addMessage(context, new FormErrorInfo(null, s), formFile, severity);
	}

	private static void addMessage(final CompileContext context, final FormErrorInfo e, final VirtualFile formFile, final CompilerMessageCategory severity)
	{
		if(formFile != null)
		{
			FormElementNavigatable navigatable = new FormElementNavigatable(context.getProject(), formFile, e.getComponentId());
			context.addMessage(severity, formFile.getPresentableUrl() + ": " + e.getErrorMessage(), formFile.getUrl(), -1, -1, navigatable);
		}
		else
		{
			context.addMessage(severity, e.getErrorMessage(), null, -1, -1);
		}
	}

	@Override
	public ValidityState createValidityState(final DataInput in) throws IOException
	{
		return TimestampValidityState.load(in);
	}

	public static VirtualFile findSourceFile(final CompileContext context, final VirtualFile formFile, final String className)
	{
		final consulo.module.Module module = context.getModuleByFile(formFile);
		if(module == null)
		{
			return null;
		}
		final PsiClass aClass = FormEditingUtil.findClassToBind(module, className);
		if(aClass == null)
		{
			return null;
		}

		final PsiFile containingFile = aClass.getContainingFile();
		if(containingFile == null)
		{
			return null;
		}

		return containingFile.getVirtualFile();
	}

	private static final class MyInstrumentationItem implements ProcessingItem
	{
		private final File myClassFile;
		private final VirtualFile myFormFile;
		private final String myClassToBindFQname;
		private final TimestampValidityState myState;

		private MyInstrumentationItem(final File classFile, final VirtualFile formFile, final String classToBindFQname)
		{
			myClassFile = classFile;
			myFormFile = formFile;
			myClassToBindFQname = classToBindFQname;
			myState = new TimestampValidityState(formFile.getTimeStamp());
		}

		@Override
		@Nonnull
		public File getFile()
		{
			return myClassFile;
		}

		public VirtualFile getFormFile()
		{
			return myFormFile;
		}

		public String getClassToBindFQname()
		{
			return myClassToBindFQname;
		}

		@Override
		public ValidityState getValidityState()
		{
			return myState;
		}
	}

}
