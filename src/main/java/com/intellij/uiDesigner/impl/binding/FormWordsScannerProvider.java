package com.intellij.uiDesigner.impl.binding;

import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.cacheBuilder.FileWordsScannerProvider;
import consulo.language.cacheBuilder.WordsScanner;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 31/01/2023
 */
@ExtensionImpl
public class FormWordsScannerProvider implements FileWordsScannerProvider
{
	@Nonnull
	@Override
	public FileType getFileType()
	{
		return GuiFormFileType.INSTANCE;
	}

	@Nonnull
	@Override
	public WordsScanner createWordsScanner()
	{
		return new FormWordsScanner();
	}
}
