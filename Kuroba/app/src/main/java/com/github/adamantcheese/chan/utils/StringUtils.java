package com.github.adamantcheese.chan.utils;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getFlavorType;

public class StringUtils {
    private static final Pattern IMAGE_THUMBNAIL_EXTRACTOR_PATTERN = Pattern.compile("/(\\d{12,32}+)s.(.*)");
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toLowerCase(Locale.ENGLISH).toCharArray();
    private static final String RESERVED_CHARACTERS = "|?*<\":>+\\[\\]/'\\\\\\s";
    private static final String RESERVED_CHARACTERS_DIR = "[" + RESERVED_CHARACTERS + "." + "]";
    private static final String RESERVED_CHARACTERS_FILE = "[" + RESERVED_CHARACTERS + "]";

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];

        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        return new String(hexChars);
    }

    @Nullable
    public static String convertThumbnailUrlToFilenameOnDisk(String url) {
        Matcher matcher = IMAGE_THUMBNAIL_EXTRACTOR_PATTERN.matcher(url);
        if (matcher.find()) {
            String filename = matcher.group(1);
            String extension = matcher.group(2);

            if (filename == null || extension == null) {
                return null;
            }

            if (filename.isEmpty() || extension.isEmpty()) {
                return null;
            }

            return String.format("%s_thumbnail.%s", filename, extension);
        }

        return null;
    }

    @Nullable
    public static String extractFileNameExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index == -1) {
            return null;
        }

        return filename.substring(index + 1);
    }

    @NonNull
    public static String removeExtensionFromFileName(String filename) {
        int index = filename.lastIndexOf('.');
        if (index == -1) {
            return filename;
        }

        return filename.substring(0, index);
    }

    public static String dirNameRemoveBadCharacters(String dirName) {
        return dirName.replaceAll(" ", "_").replaceAll(RESERVED_CHARACTERS_DIR, "");
    }

    /**
     * The same as dirNameRemoveBadCharacters but allows dots since file names can have extensions
     */
    public static String fileNameRemoveBadCharacters(String filename) {
        return filename.replaceAll(" ", "_").replaceAll(RESERVED_CHARACTERS_FILE, "");
    }

    @Nullable
    public static String decodeBase64(String base64Encoded) {
        byte[] bytes;

        try {
            bytes = Base64.decode(base64Encoded, Base64.DEFAULT);
        } catch (Throwable error) {
            Logger.e("decodeBase64", "Error decoding base64 string! Msg: " + error.getMessage());
            return null;
        }

        return bytesToHex(bytes);
    }

    public static String maskPostNo(int postNo) {
        String postNoString = String.valueOf(postNo);
        if (getFlavorType() == AndroidUtils.FlavorType.Dev) {
            return postNoString;
        }

        if (postNoString.length() >= 4) {
            return postNoString.substring(0, postNoString.length() - 3) + "XXX";
        }

        return postNoString;
    }

    public static String maskImageUrl(@NonNull String url) {
        if (getFlavorType() == AndroidUtils.FlavorType.Dev) {
            return url;
        }

        if (url.length() < 4) {
            return url;
        }

        String extension = extractFileNameExtension(url);

        int extensionLength = extension == null ? 0 : (extension.length() + 1);
        int charactersToTrim = 3 + extensionLength;

        if (url.length() < charactersToTrim) {
            return url;
        }

        String trimmedUrl = url.substring(0, url.length() - charactersToTrim);
        return trimmedUrl + "XXX" + (extension == null ? "" : "." + extension);
    }

    public static boolean endsWithAny(String s, String[] suffixes) {
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
