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

import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.impl.GuiDesignerConfiguration;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.compiler.*;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.util.CompilerUtil;
import consulo.document.FileDocumentManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.ExceptionUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import jakarta.annotation.Nonnull;
import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@ExtensionImpl
public final class Form2SourceCompiler implements SourceInstrumentingCompiler
{
	@Override
	@Nonnull
	public String getDescription()
	{
		return UIDesignerBundle.message("component.gui.designer.form.to.source.compiler");
	}

	@Override
	public boolean validateConfiguration(CompileScope scope)
	{
		return true;
	}

	@Override
	@Nonnull
	public ProcessingItem[] getProcessingItems(final CompileContext context)
	{
		final Project project = context.getProject();
		if(GuiDesignerConfiguration.getInstance(project).INSTRUMENT_CLASSES)
		{
			return ProcessingItem.EMPTY_ARRAY;
		}

		final ArrayList<ProcessingItem> items = new ArrayList<>();

		ApplicationManager.getApplication().runReadAction(new Runnable()
		{
			@Override
			public void run()
			{
				CompileScope scope = context.getCompileScope();
				CompileScope projectScope = CompilerManager.getInstance(project).createProjectCompileScope();

				VirtualFile[] formFiles = projectScope.getFiles(GuiFormFileType.INSTANCE);
				CompilerManager compilerManager = CompilerManager.getInstance(project);
				BindingsCache bindingsCache = new BindingsCache(project);

				try
				{
					HashMap<String, VirtualFile> class2form = new HashMap<>();

					for(VirtualFile formFile : formFiles)
					{
						if(compilerManager.isExcludedFromCompilation(formFile))
						{
							continue;
						}

						String classToBind;
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
							addError(context, new FormErrorInfo(null, UIDesignerBundle.message("error.cannot.process.form.file", e)), formFile);
							continue;
						}

						if(classToBind == null)
						{
							continue;
						}

						VirtualFile sourceFile = Form2ByteCodeCompiler.findSourceFile(context, formFile, classToBind);
						if(sourceFile == null)
						{
							if(scope.belongs(formFile.getUrl()))
							{
								addError(context, new FormErrorInfo(null, UIDesignerBundle.message("error.class.to.bind.does.not.exist", classToBind)), formFile);
							}
							continue;
						}

						boolean inScope = scope.belongs(sourceFile.getUrl()) || scope.belongs(formFile.getUrl());

						VirtualFile alreadyProcessedForm = class2form.get(classToBind);
						if(alreadyProcessedForm != null)
						{
							if(inScope)
							{
								addError(context, new FormErrorInfo(null, UIDesignerBundle.message("error.duplicate.bind", classToBind, alreadyProcessedForm.getPresentableUrl())), formFile);
							}
							continue;
						}
						class2form.put(classToBind, formFile);

						if(!inScope)
						{
							continue;
						}

						items.add(new MyInstrumentationItem(sourceFile, formFile));
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

	@Override
	public ProcessingItem[] process(final CompileContext context, ProcessingItem[] items)
	{
		final ArrayList<ProcessingItem> compiledItems = new ArrayList<>();

		context.getProgressIndicator().setText(UIDesignerBundle.message("progress.compiling.ui.forms"));

		int formsProcessed = 0;

		final Project project = context.getProject();
		final FormSourceCodeGenerator generator = new FormSourceCodeGenerator(project);

		final HashSet<consulo.module.Module> processedModules = new HashSet<>();

		final List<File> filesToRefresh = new ArrayList<>();
		for(ProcessingItem item1 : items)
		{
			context.getProgressIndicator().setFraction((double) (++formsProcessed) / ((double) items.length));

			final MyInstrumentationItem item = (MyInstrumentationItem) item1;

			final VirtualFile formFile = item.getFormFile();

      /*if(GuiDesignerConfiguration.getInstance(project).COPY_FORMS_TO_OUTPUT) {
		VirtualFile outputForFile = context.getOutputForFile(module, formFile);
        if (outputForFile != null) {
          String packageName = directoryIndex.getPackageName(formFile.getParent());

          File outputFormFile = null;
          if (packageName == null || packageName.isEmpty()) {
            outputFormFile = new File(outputForFile.getPath(), formFile.getName());
          }
          else {
            outputFormFile = new File(outputForFile.getPath(), packageName.replace(".", "/") + "/" + formFile.getName());
          }

          FileUtil.createParentDirs(outputFormFile);
          try {
            FileUtil.copy(new File(formFile.getPath()), outputFormFile);
          }
          catch (IOException e) {
            addError(
              context,
              new FormErrorInfo(null, UIDesignerBundle.message("error.cannot.copy.gui.designer.form.runtime",
                                                               module.getName(), ExceptionUtil.getThrowableText(e))),
              null
            );
          }
        }
      }   */

			if(GuiDesignerConfiguration.getInstance(project).COPY_FORMS_RUNTIME_TO_OUTPUT)
			{
				ApplicationManager.getApplication().runReadAction(new Runnable()
				{
					@Override
					public void run()
					{
						consulo.module.Module module = ModuleUtilCore.findModuleForFile(formFile, project);
						if(module != null && !processedModules.contains(module))
						{
							processedModules.add(module);
							String moduleOutputPath = CompilerPaths.getModuleOutputPath(module, false);
							try
							{
								if(moduleOutputPath != null)
								{
									filesToRefresh.addAll(CopyResourcesUtil.copyFormsRuntime(moduleOutputPath, false));
								}
								String testsOutputPath = CompilerPaths.getModuleOutputPath(module, true);
								if(testsOutputPath != null && !testsOutputPath.equals(moduleOutputPath))
								{
									filesToRefresh.addAll(CopyResourcesUtil.copyFormsRuntime(testsOutputPath, false));
								}
							}
							catch(IOException e)
							{
								addError(
										context,
										new FormErrorInfo(null, UIDesignerBundle.message("error.cannot.copy.gui.designer.form.runtime",
												module.getName(), ExceptionUtil.getThrowableText(e))),
										null
								);
							}
						}
					}
				});
			}

			ApplicationManager.getApplication().invokeAndWait(new Runnable()
			{
				@Override
				public void run()
				{
					CommandProcessor.getInstance().executeCommand(project, new Runnable()
					{
						@Override
						public void run()
						{
							ApplicationManager.getApplication().runWriteAction(new Runnable()
							{
								@Override
								public void run()
								{
									PsiDocumentManager.getInstance(project).commitAllDocuments();
									generator.generate(formFile);
									ArrayList<FormErrorInfo> errors = generator.getErrors();
									if(errors.size() == 0)
									{
										compiledItems.add(item);
									}
									else
									{
										for(FormErrorInfo e : errors)
										{
											addError(context, e, formFile);
										}
									}
								}
							});
						}
					}, "", null);
					FileDocumentManager.getInstance().saveAllDocuments();
				}
			}, ApplicationManager.getApplication().getNoneModalityState());
		}

		CompilerUtil.refreshIOFiles(filesToRefresh);
		return compiledItems.toArray(new ProcessingItem[compiledItems.size()]);
	}

	private static void addError(CompileContext context, FormErrorInfo e, VirtualFile formFile)
	{
		if(formFile != null)
		{
			FormElementNavigatable navigatable = new FormElementNavigatable(context.getProject(), formFile, e.getComponentId());
			context.addMessage(CompilerMessageCategory.ERROR,
					formFile.getPresentableUrl() + ": " + e.getErrorMessage(),
					formFile.getUrl(), -1, -1, navigatable);
		}
		else
		{
			context.addMessage(CompilerMessageCategory.ERROR, e.getErrorMessage(), null, -1, -1);
		}
	}

	@Override
	public ValidityState createValidityState(DataInput in) throws IOException
	{
		return TimestampValidityState.load(in);
	}

	private static final class MyInstrumentationItem implements ProcessingItem
	{
		@Nonnull
		private final VirtualFile mySourceFile;
		private final VirtualFile myFormFile;
		private final TimestampValidityState myState;

		public MyInstrumentationItem(@Nonnull VirtualFile sourceFile, VirtualFile formFile)
		{
			mySourceFile = sourceFile;
			myFormFile = formFile;
			myState = new TimestampValidityState(formFile.getTimeStamp());
		}

		@Override
		@Nonnull
		public File getFile()
		{
			return VirtualFileUtil.virtualToIoFile(mySourceFile);
		}

		public VirtualFile getFormFile()
		{
			return myFormFile;
		}

		@Override
		public ValidityState getValidityState()
		{
			return myState;
		}
	}

}
