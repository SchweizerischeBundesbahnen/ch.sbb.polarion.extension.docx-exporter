package ch.sbb.polarion.extension.docx_exporter.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileResourceProvider {

    @Nullable
    String getResourceAsBase64String(@NotNull String resource);

    byte[] getResourceAsBytes(@NotNull String resource);

}
