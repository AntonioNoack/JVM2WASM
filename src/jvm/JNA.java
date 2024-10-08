package jvm;

import annotations.Alias;
import com.sun.jna.platform.FileUtils;

import java.io.File;

public class JNA {
    private static final FileUtils FILE_UTILS = new FileUtils() {
        @Override
        public void moveToTrash(File... files) {
            throw new IllegalStateException("Cannot move files to trash in Web");
        }

        @Override
        public boolean hasTrash() {
            return false;
        }
    };

    @Alias(names = "com_sun_jna_platform_FileUtils_getInstance_Lcom_sun_jna_platform_FileUtils")
    public static FileUtils com_sun_jna_platform_FileUtils_getInstance_Lcom_sun_jna_platform_FileUtils() {
        return FILE_UTILS;
    }
}
